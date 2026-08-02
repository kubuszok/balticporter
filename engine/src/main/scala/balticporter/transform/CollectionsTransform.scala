package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, RequiresRuntime, RuntimeArtifact, SurfacePolicy}
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
  *
  * ==WHERE it applies — the [[balticporter.tir.RuleScope]] parameter==
  * The MAPPING is §1(a) (`java.util.List` is `mutable.Buffer` in every port there will ever be);
  * WHICH DECLARATIONS it reaches is §1(b), because a library may have a type that must keep the JDK
  * shape — a hand-written bridge, a class whose whole purpose is to hand a real `java.util.List` to
  * something outside the port. Before the scope existed, the only way to say so was not to run the
  * phase at all.
  *
  *   - `RuleScope.Everywhere()` — the default, and the pre-scope behaviour BY THE SAME CODE PATH:
  *     nothing is excluded, so the `excluded` set is empty and every branch below collapses to what
  *     it did before. §1(b)'s "an empty parameter needs no code path", taken literally.
  *   - `RuleScope.Everywhere(except)` — retype everything but those declarations.
  *   - `RuleScope.Only(include)` — retype only those, GROWN by [[FlowPropagation]]: a named field
  *     carries its getter, its setter's parameter and every local it initialises, because a
  *     signature that moves without its call sites is a compile error one call away.
  *
  * The scope's unit is a MEMBER — a field, a method or a nested type — because that is the unit a
  * BODY is rewritten in: a method whose parameter becomes a `Buffer` has a body full of scala-shaped
  * calls, and half a body is not a translation. So a propagated parameter or local pulls its
  * enclosing member in whole, and a `TypeDef` or a bare statement in a class body follows its class.
  *
  * ==The seam a scope creates cannot be closed, and is therefore COUNTED==
  * `coerce` bridges a scala collection into a `balticporter.runtime` shim slot; there is no such
  * bridge into a REAL `java.util.List` slot, and there cannot be one — a `mutable.Buffer` is not a
  * `java.util.List` and the shims deliberately implement neither. So a scope boundary is refused and
  * reported rather than approximated (ENGINE-LIMITS M6): [[scopedOut]] is handed to
  * [[CollectionBoundaryCheck]], which reports every such slot as `Issue.ScopedOut` with the §1(b)
  * fix. A scope that silently produced an uncompilable seam would be worse than no scope, and this
  * is what makes it not one.
  */
final class CollectionsTransform(
    val scope: RuleScope = RuleScope.Everywhere(),
    /** RETARGET ENTRIES — java FQN → scala FQN, retyped at every occurrence and API-mapped NOWHERE.
      *
      * ==Why this is a second table and not four more rows in [[CollectionsTransform.typeMap]]==
      * `typeMap` says two things at once: *this type becomes that one*, and *its calls are rewritten
      * kind-aware and its slots bridged by `coerce`*. That second half is what a collection needs
      * (`list.get(0)` is `xs(0)`; a `Buffer` reaching a `java.util.Iterable` slot needs a shim) and
      * it is exactly what a retarget must NOT get. So a retarget entry joins `remap` — the type
      * rewrite — and joins neither `kindOf` nor any factory, which makes every kind-driven arm a
      * no-op on it by arithmetic rather than by a new guard in each one.
      *
      * ==The precondition, which the engine cannot check and the policy author owes==
      * '''The scala target must be usable wherever the java source was.''' The worked example is
      * `java.util.Comparator` → `scala.math.Ordering`: Scala declares
      * `trait Ordering[T] extends Comparator[T]`, so every occurrence moves with no coercion
      * anywhere, an anonymous `new Comparator<T>(){ int compare(a,b) }` becomes a structurally
      * identical `new Ordering[T]`, a java lambda stays SAM-convertible (`compare` is `Ordering`'s
      * one abstract member), and a JDK method still declaring `Comparator` accepts the retyped
      * value unchanged. Where that relation does not hold, the seam is a `coerce` boundary and the
      * type belongs in `typeMap` with a kind and a factory — not here.
      *
      * A key that also appears in `typeMap` is REFUSED rather than merged: two answers for one type
      * is a rewrite whose outcome depends on which table was read, which is not a thing a policy
      * author can reason about. Empty is the default and makes this a no-op with no code path. */
    val retarget: Map[String, String] = Map.empty,
) extends Phase, RequiresRuntime, PolicySource, SurfacePolicy, PolicyBound:
  def name = "java-collections->scala"

  /** What the RUN resolved each declared scope entry to, before the pipeline started (§8.1). This
    * phase is the one whose own matcher already reports [[balticporter.tir.NotBound.ExternalOnly]]
    * (see `applyScope`), so the `policy-binding` measurement over the corpus is largely a
    * measurement of whether the binder reproduces the answer this phase worked out by hand. */
  private var boundScope: Map[String, Binding[Unit]] = Map.empty

  /** …and each retarget SOURCE. Bound as [[Ownership.Either]] on purpose: a retarget's subject is a
    * type this program REFERENCES and never declares — a JDK interface — which is the one shape
    * `Owned` would report as never-matched while the rewrite worked. */
  private var boundRetarget: Map[String, Binding[SymId]] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val setting = s"CollectionsTransform(scope) ${scope.productPrefix} entry"
    boundScope = scope.entries.toList.sorted.map(e => e -> binder.bindScope(name, setting, e)).toMap
    boundRetarget = retarget.keys.toList.sorted
      .map(k => k -> binder.bindType(name, RetargetSetting, k, Ownership.Either)).toMap

  /** Two modules that scope this phase differently emit incompatible signatures for the shared
    * surface — a `java.util.List` parameter in the base against a `Buffer` argument in the
    * dependent, which each compile alone and cannot compile together. That is exactly what
    * [[SurfacePolicy]] exists to make comparable (CLAUDE.md §1.5); before the scope there was
    * nothing to compare, which is why this phase did not implement it. The default scope renders
    * `""`, so a port that sets no scope has the fingerprint it always effectively had.
    *
    * A RETARGET is the same fact one type further out — a base whose `Comparator`s became
    * `Ordering`s and a dependent whose did not emit signatures that cannot meet — so it joins the
    * fingerprint. Sorted, and only rendered when non-empty, so a port that declares none has the
    * string it always had and no baseline moves. */
  def surfaceFingerprint: String =
    if retarget.isEmpty then scope.fingerprint
    else s"${scope.fingerprint};retarget=${retarget.toList.sorted.map((k, v) => s"$k->$v").mkString(",")}"

  /** this phase retypes onto `balticporter.runtime` — declared once, so the run derives the port's
    * dependency, its vendored sources and the emitter's external-parent table from it. */
  def runtimeTypes: Set[String] = CollectionsTransform.runtimeTypes

  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  /** java fully-qualified name → (scala fully-qualified name, collection kind).
    *
    * The table itself lives in the COMPANION ([[CollectionsTransform.typeMap]]) — it is a constant
    * and nothing about it depends on an instance, and `JdkSurfaceCheck` has to be able to ask what
    * this phase retypes without constructing one (§4.56: a check concludes from what a phase DID,
    * which means the phase's record has to be reachable). */
  private val typeMap: Map[String, (String, Kind)] = CollectionsTransform.typeMap

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

  /** …held to the units the run EMITS, for the reason [[closure]] gives. [[scopedOut]] goes with
    * the mapping, so a seam the SCOPE created is classified as such rather than as an engine bug. */
  def boundary(program: Program, units: List[Tree.ClassDef]): List[CollectionBoundaryCheck.Finding] =
    CollectionBoundaryCheck.check(program, units, mappedTypes, retypedTargets, scopedOut) ++
      // …plus the EXTERNAL seams, which the check cannot re-derive: by the time it runs, the
      // position-blind retyping has moved the node's type on BOTH sides of every one of them, so a
      // walk over the post-phase tree reports zero. They are recorded during the traversal, while
      // the external signature is still readable, and filtered to the units this run EMITS for
      // ENGINE-LIMITS D2's reason — a dependent's program contains its base's units, and a seam
      // inside one of those is the base's finding.
      externalSeams.toList.filter(f => emittedPaths(units).contains(f.origin.javaPath))

  /** the java files the units this run emits came from — the D2 filter, by SOURCE PATH, because a
    * recorded seam carries its `Origin` and not the unit it sat in. */
  private def emittedPaths(units: List[Tree.ClassDef]): Set[String] =
    units.map(_.origin.javaPath).toSet

  /** [[RetargetBoundaryCheck]] over this phase's own retarget table — the PRODUCER direction, which
    * [[boundary]] is blind to by construction (a retarget contributes nothing to `mappedTypes` or
    * `retypedTargets`, because its precondition says there is no seam). Run on the program AFTER
    * the phase, for the same reason: it counts what the retyping created. */
  def retargetBoundary(program: Program): List[RetargetBoundaryCheck.Finding] =
    retargetBoundary(program, program.units)

  /** …held to the units the run EMITS (`ENGINE-LIMITS.md` D2), exactly as [[boundary]] is. */
  def retargetBoundary(program: Program, units: List[Tree.ClassDef]): List[RetargetBoundaryCheck.Finding] =
    RetargetBoundaryCheck.check(program, units, effectiveRetarget)

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
  /** each HASHED scala collection symbol → its companion's `defaultLoadFactor`, for
    * [[capacityConstructor]]. Keyed on the phase's OWN targets, exactly as `fromSyms` is. */
  private var loadFactorSyms: Map[SymId, SymId] = Map.empty
  /** `JavaCollections`' statics, by name — see `sym`. */
  private var staticSyms: Map[String, SymId] = Map.empty
  /** the `java.util.stream` collapse — see `staticRewrite`. */
  private var asScalaBufferSym, filteredSym: SymId = SymId.None
  /** scala's own `toBuffer` — how a `Kind.Set` or `Kind.Map` stream SOURCE reaches the `Buffer`
    * every collapsed operation is declared over. See `streamSource`. */
  private var toBufferSym: SymId = SymId.None
  /** `mutable.Buffer`, so a collapsed stream can be TYPED as what it now emits. */
  private var bufferSym: SymId = SymId.None
  /** scala's own `sum` — a plain MEMBER name on the collapsed buffer, not a `JavaCollections` helper. */
  private var sumSym: SymId = SymId.None
  /** scala's own `map` — a plain member on a collapsed buffer, for the stream chain. */
  private var mapSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private var iteratorFromSym, javaIteratorSym: SymId = SymId.None
  /** the mapping targets `JavaCollections.fromJava` can actually PRODUCE — see
    * [[CollectionsTransform.liveWrappable]], read as symbols so [[externalProducer]] asks a
    * membership question about what this run minted rather than a question about a name. EMPTY when
    * the program names none of them, which makes the wrap arm decline by arithmetic. */
  private var liveWrappableSyms: Set[SymId] = Set.empty

  // ---- the RuleScope's own record, for THIS run (see `applyScope`) ----

  /** `JavaCollections.fromJava` / `toJava` — the EXTERNAL seam's two directions. */
  private var fromJavaSym, toJavaSym: SymId = SymId.None

  /** java's three `Object`-keyed map members, for a receiver whose type arguments are WILDCARDS —
    * see [[wildcardMapCall]]. */
  private var mapGetSym, mapContainsKeySym, mapRemoveSym: SymId = SymId.None

  /** the java symbols this run's mapping sends to a target that CANNOT BE A PARENT — see
    * [[restoreUninheritableParents]]. EMPTY unless the program actually names one, which makes the
    * pass a no-op by arithmetic on every port that does not. */
  private var uninheritableSyms: Set[SymId] = Set.empty

  /** is this symbol one the PROGRAM declares? Structural (`Program.owned`), never a name test
    * (§4.56), and computed once per run because the external-seam arms ask it per call. */
  private var ownedSym: SymId => Boolean = _ => true

  /** every symbol THIS PHASE minted in [[run]] — the rewrites' own targets. They are owned by
    * nothing and named by no class file, so the external-seam arms would otherwise read each of
    * them as a third party's method. */
  private var mintedSyms: Set[SymId] = Set.empty

  /** every external seam this run could NOT close, in the order it met them. Reported through
    * [[boundary]], because it is the same residue `CollectionBoundaryCheck` counts and a reader
    * looking for "what did the retyping leave open" must find all of it in one place. */
  private val externalSeams = collection.mutable.ListBuffer[CollectionBoundaryCheck.Finding]()

  /** every symbol this run's [[scope]] held OUT of the rewrite. EMPTY for the default scope — and
    * for any scope whose entries matched nothing — which is what makes the no-op a no-op. */
  private var excluded: Set[SymId] = Set.empty

  /** …read back, so [[CollectionBoundaryCheck]] can classify a seam the scope created from the
    * phase's OWN record of what it held back rather than guessing from a type name (§4.56). */
  def scopedOut: Set[SymId] = excluded

  /** the declaration → the scope ENTRY that admitted it, for `Reason.Configured`'s key (§4.575:
    * the key is the manifest entry VERBATIM, because it is the string an agent edits). */
  private var admittedBy: Map[SymId, String] = Map.empty

  private var report: PolicyReport = PolicyReport.empty

  /** the setting every retarget finding is filed under — the string an agent greps for (§4.575). */
  private val RetargetSetting = "CollectionsTransform(retarget) entry"

  /** the retarget entries that actually RUN: everything the port declared, minus any key
    * [[CollectionsTransform.typeMap]] already answers for (reported as `Malformed` instead — see
    * the constructor parameter). A `val`, so the two readers below and `run` cannot disagree. */
  private val effectiveRetarget: Map[String, String] =
    retarget.filterNot((k, _) => typeMap.contains(k))

  /** what a RETARGET entry moved, read back so a reader of a finding or a decision has both halves.
    * Deliberately NOT folded into [[mappedTypes]] / [[retypedTargets]]: those two feed
    * [[CollectionClosureCheck]] and [[CollectionBoundaryCheck]], which are about the shim BOUNDARY,
    * and a retarget has none by construction (its target is usable wherever its source was — the
    * precondition stated on the constructor parameter). */
  def retargetedTypes: Map[String, String] = effectiveRetarget

  /** Scope entries that named nothing in this run — a §1(b) silent no-op, which is the failure this
    * whole channel exists for: a mis-typed exclusion leaves the phase rewriting a type the port
    * meant to protect, and nothing else in the pipeline can see it. Reflects the last [[run]];
    * empty before the first, and empty for the default scope.
    *
    * The RETARGET half is a property of the policy and the program alone, so it is complete the
    * moment the keys are bound and does not wait for a run. */
  def policyReport: PolicyReport =
    report ++ PolicyReport.fromBindings(boundRetarget.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, RetargetSetting, k, b.forget)
    }) ++ PolicyReport(
      retarget.keys.toList.sorted.filter(typeMap.contains).map { k =>
        PolicyFinding(name, RetargetSetting, k, PolicyIssue.Malformed,
          s"`$k` already has a COLLECTION mapping (-> ${targetOf(k)}), which retypes its call " +
            "shapes and bridges its slots as well as moving the type. A retarget entry does only " +
            "the last of those, so the two answers are not refinements of one another — the entry " +
            "is ignored and the collection mapping stands")
      })

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
      // …RETARGET first, so a key the port also finds in `typeMap` cannot silently take the
      // collection answer: `effectiveRetarget` has already removed any such key and reported it.
      effectiveRetarget.get(s.fullName).orElse(typeMap.get(s.fullName).map(_._1)).map { sc =>
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
    toBufferSym         = mint("toBuffer", "toBuffer")
    staticSyms = CollectionsTransform.StaticHelpers
      .map(n => n -> mint(n, s"$JavaCollectionsFqn.$n")).toMap
    // one `from` per DISTINCT scala target, so `new ArrayList<>(c)` copies through the companion the
    // target type actually has. `Tuple2` is excluded: it is a `Kind.Entry`, not a collection, and
    // `Tuple2.from` does not exist — the `kindOf` gate in `copyConstructor` never offers it one.
    fromSyms = byScala.collect {
      case (fqn, id) if fqn.startsWith("scala.collection.") => id -> mint("from", s"$fqn.from")
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
    // …the five targets a LIVE view exists for, as this run's own symbols. Keyed on `byScala`, so a
    // target the program never names is simply absent and the wrap declines by arithmetic.
    liveWrappableSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.liveWrappable(fqn) => id
    }.toSet
    foreachSym          = mint("foreach", "foreach")
    removeHeadOptionSym = mint("removeHeadOption", "removeHeadOption")
    headOptionSym       = mint("headOption", "headOption")
    orNullSym           = mint("orNull", "orNull")
    prependSym          = mint("prepend", "prepend")
    putSym       = mint("put", "put")     // scala `mutable.Map.put`: returns the PREVIOUS value
    removeSym    = mint("remove", "remove") // scala `mutable.Map.remove`: returns the REMOVED value
    fromJavaSym  = staticSyms.getOrElse("fromJava", SymId.None)
    toJavaSym    = staticSyms.getOrElse("toJava", SymId.None)
    mapGetSym         = staticSyms.getOrElse("mapGet", SymId.None)
    mapContainsKeySym = staticSyms.getOrElse("mapContainsKey", SymId.None)
    mapRemoveSym      = staticSyms.getOrElse("mapRemove", SymId.None)
    externalSeams.clear()
    implicitPending.clear()

    mintedSyms = added.map(_.id).toSet
    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = program.rebuilt(symbols = symbols)
    // …resolved once. The external-seam arms ask it per CALL, and `Program.owned` walks an owner
    // chain, so asking it inside the traversal would be quadratic on a library of any size.
    val ownedNow = summon[Program].owned
    ownedSym = ownedNow
    applyScope(summon[Program]) // fills `excluded`, `admittedBy` and `report` — a no-op by default
    uninheritableSyms = program.symbols.all.collect {
      case s if typeMap.get(s.fullName).exists((tgt, _) => CollectionsTransform.UninheritableTargets(tgt)) => s.id
    }.toSet
    val units    = program.units.map(u =>
      restoreUninheritableParents(u, restoreExcluded(u, StandardTraversal.mapClassDef(this, u))))
    val symbols2 = mapSignatures(symbols) // retype signatures too
    recordRetypings(symbols, symbols2)
    recordScopedOut(symbols)
    program.rebuilt(units, symbols2)

  // -------------------------------------------------------------------------
  // RuleScope — WHICH declarations this run rewrites (CLAUDE.md §1(b))
  // -------------------------------------------------------------------------

  /** Decide the scope for this run: fill [[excluded]], [[admittedBy]] and [[report]].
    *
    * An unrestricted scope short-circuits to EMPTY, and so does any scope whose entries match
    * nothing — which is what makes the parameter's default a no-op by arithmetic rather than by a
    * branch that has to stay in step (§1(b)).
    *
    * The two directions are not symmetric, and neither is an accident:
    *
    *   - `Everywhere(except)` — an opt-out is ABSOLUTE. It is a statement about a declaration that
    *     must keep the JDK shape, so nothing may drag it back in, propagation included.
    *   - `Only(include)` — an opt-in is a SEED. Retyping a field without its getter is a compile
    *     error one call away, so the named declarations are grown along pure-move flows
    *     ([[FlowPropagation]]), once per ENTRY over a shared edge set so that every admitted
    *     declaration can name the entry that reached it (§4.575).
    *
    * Eligibility for the growth is this phase's OWN record — "does `typeMap` cover the head of this
    * symbol's type" — never a name test, which is CLAUDE.md §4.56's general form.
    */
  private def applyScope(p: Program): Unit =
    excluded = Set.empty; admittedBy = Map.empty; report = PolicyReport.empty
    if scope.isUnrestricted then return

    // OWNED symbols only, and the reason is CLAUDE.md §4.56 applied to a scope: the symbol table
    // holds every EXTERNAL the frontend interned on first reference, `java.util.List` among them,
    // so an entry naming a JDK type matches — the external symbol, which has no declaration to hold
    // back and no body to leave alone. The phase then does exactly nothing while the entry counts as
    // FIRED, which is the §1(b) silent no-op this whole channel exists to catch, wearing the
    // disguise of a policy that works. Ownership is structural (`Program.owned`), never a name test.
    val owned = p.owned
    val named: List[(Symbol, String)] =
      p.symbols.all.toList.flatMap(s => if owned(s.id) then scope.entryFor(p, s).map(s -> _) else scala.None)
    val fired = named.map(_._2).toSet
    // …and an entry whose ONLY matches were external is reported with WHY, because "no such name in
    // this program" would be a lie an agent would spend a session disproving: the name is right, the
    // knob is wrong. A plain name test is exactly right here — the question is not what this symbol
    // is, it is what the AUTHOR of the entry was pointing at.
    val externalOnly = p.symbols.all.iterator
      .filterNot(s => owned(s.id))
      .flatMap(s => RuleScope.longestPrefix(s.fullName, scope.entries))
      .toSet -- fired

    scope match
      case RuleScope.Everywhere(_) =>
        excluded = named.map(_._1.id).toSet
      case RuleScope.Only(include) =>
        val es       = FlowPropagation.edges(p)
        def eligible(id: SymId): Boolean = p.symbolOf(id).exists(s => collectionValued(p, s.info))
        // one growth per entry, sorted, so the attribution is deterministic when two entries reach
        // the same declaration; `admittedBy` keeps the first (i.e. the alphabetically first entry).
        val byEntry = include.toList.sorted.map { e =>
          val seeds = named.collect { case (s, `e`) => s.id }.toSet
          e -> (seeds ++ FlowPropagation.grow(es, seeds, eligible))
        }
        val pulled = byEntry.flatMap((_, ids) => ids ++ ids.flatMap(memberOf(p, _))).toSet
        admittedBy = byEntry.reverse.flatMap((e, ids) => ids.map(_ -> e)).toMap
        excluded   = p.symbols.all.collect { case s if !ownerChain(p, s.id).exists(pulled) => s.id }.toSet

    report = PolicyReport(scope.neverFired(fired).toList.sorted.map { k =>
      PolicyFinding(name, s"CollectionsTransform(scope) ${scope.productPrefix} entry", k, PolicyIssue.NeverMatched,
        if externalOnly(k) then
          "this names a type THIS PROGRAM DOES NOT DECLARE — an external the frontend interned on " +
            "first reference, a JDK type most likely. A scope selects DECLARATIONS the port emits, " +
            "and there is no such declaration to hold back or admit, so the phase ran as if the " +
            "entry were absent. The JDK side of this phase is the MAPPING, not the scope: to stop " +
            "`java.util.List` becoming a scala collection everywhere, that is a `typeMap` question; " +
            "to keep it in ONE of your own classes, name that class."
        else
          "no package, type or member with this fully-qualified name occurs in this program, so the " +
            "scope neither held anything back nor admitted anything — the phase ran as if the entry " +
            "were absent")
    })

  /** could this phase retype what this symbol DECLARES? A value's own type, or a method's RESULT —
    * the two positions a pure-move flow moves a collection through. */
  private def collectionValued(p: Program, info: TypeRepr): Boolean = info match
    case TypeRepr.MethodType(_, r, _)                       => isMapped(p, r)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) => isMapped(p, r)
    case other                                              => isMapped(p, other)

  private def isMapped(p: Program, t: TypeRepr): Boolean =
    headSym(t).flatMap(p.symbolOf)
      .exists(s => typeMap.contains(s.fullName) || effectiveRetarget.contains(s.fullName))

  /** a symbol and every owner above it, fuel-bounded. */
  private def ownerChain(p: Program, id: SymId, fuel: Int = 64): List[SymId] =
    if id == SymId.None || fuel <= 0 then Nil
    else id :: p.symbolOf(id).toList.flatMap(s => ownerChain(p, s.owner, fuel - 1))

  /** the nearest ancestor-or-self that is a MEMBER of a type rather than of a method — the unit a
    * body is rewritten in. A propagated parameter or local pulls this in with it, because half a
    * rewritten body is not a translation. */
  private def memberOf(p: Program, id: SymId): Option[SymId] =
    ownerChain(p, id).find(x => !p.symbolOf(x).flatMap(s => p.symbolOf(s.owner)).exists(o => isMethodLike(o.info)))

  private def isMethodLike(t: TypeRepr): Boolean = t match
    case _: TypeRepr.MethodType => true
    case _: TypeRepr.PolyType   => true
    case _                      => false

  /** Restore the members this run's scope held back.
    *
    * The traversal runs over the WHOLE unit and the excluded members are then spliced back from the
    * original, rather than the walk being taught to skip them: `StandardTraversal.mapClassDef` is
    * the one walk in the engine kept complete as node kinds are added (CLAUDE.md §3), and a second
    * walk that knew how to descend selectively would be exactly the hand-rolled recursion that rule
    * forbids. What is hand-written here is only the DECLARATION SPINE — a class body is a list of
    * class/def/val/type/statement, and the mapped list is the same length and the same kinds in the
    * same order, so the two zip exactly.
    *
    * `excluded.isEmpty` — the default, and any scope that matched nothing — returns the mapped unit
    * untouched, which is byte-for-byte the pre-scope path.
    *
    * A `TypeDef` and a bare statement (a `static { }` block) carry no member identity of their own
    * and follow their enclosing class, as the scope's granularity says they do.
    *
    * An excluded CLASS restores only its OWN positions — parents, self type, type parameters, enum
    * cases — and never short-circuits its body. Returning the whole original class instead was the
    * first shape of this and it is wrong in exactly the case `RuleScope.Only` is made of: an entry
    * naming `Model#items` does not name `Model`, so the enclosing class is out of scope while one
    * of its members is in it, and bailing at the class threw the member's rewrite away. */
  private def restoreExcluded(orig: Tree.ClassDef, mapped: Tree.ClassDef): Tree.ClassDef =
    if excluded.isEmpty then mapped
    else
      val body = CollectionsTransform.spine(orig.body, mapped.body, orig.symbol).map {
        case (o: Tree.ClassDef, m: Tree.ClassDef) => restoreExcluded(o, m)
        case (o: Tree.DefDef, m: Tree.DefDef)     => if excluded(o.symbol) then o else m
        case (o: Tree.ValDef, m: Tree.ValDef)     => if excluded(o.symbol) then o else m
        case (_, m)                               => m
      }
      val own =
        if !excluded(orig.symbol) then mapped
        else mapped.copy(parents = orig.parents, selfType = orig.selfType,
                         tparams = orig.tparams, enumCases = orig.enumCases)
      own.copy(body = body)

  /** A PARENT this phase's target cannot BE is left as java's, and the refusal is COUNTED.
    *
    * `java.util.Map.Entry` is the case, and it is the whole of it today. As a USE it is a pair and
    * `scala.Tuple2` is exact — an entry read out of a map really is a `(K, V)`, which is why
    * `entrySet()` can hand back the map itself. As a PARENT it is impossible three times over:
    * `Tuple2` is FINAL, it has no `setValue`, and its constructor takes the two components — so a
    * class that IMPLEMENTS `Map.Entry` emits `extends scala.Tuple2[K, V]` and there is nothing
    * inside the class that could fix any of the three.
    *
    * **A phase may not emit a parent its target cannot be.** So the parent stays JAVA's: the class
    * really does implement `java.util.Map.Entry`, which is on the classpath and whose three members
    * it already declares, so the class itself compiles — and the seam moves to the SLOTS where the
    * port hands such a class to a `Tuple2`, which is where a reader can act on it. That is M6's
    * bar met by construction rather than by leaving a broken emission in place.
    *
    * The alternative — a SECOND target for the implements-case, a `JavaMapEntry` shim beside the
    * `Tuple2` the use-case keeps — is rejected, and the reason is not effort. Every `entrySet()` in
    * every port yields a `Tuple2`, so the two would meet at every crossing, in both directions,
    * needing a coercion each way for one class in one library: a second truth about one java type,
    * which §1's balance is explicit about (ENGINE-LIMITS K5.7).
    *
    * A no-op by arithmetic wherever the program names no such type ([[uninheritableSyms]] empty),
    * which is 13 of the 14 corpus ports. */
  private def restoreUninheritableParents(orig: Tree.ClassDef, mapped: Tree.ClassDef)(using Program): Tree.ClassDef =
    if uninheritableSyms.isEmpty then mapped
    else
      def tpeOf(p: Term | TypeTree): TypeRepr = p match
        case tt: TypeTree => tt.tpe
        case t: Term      => t.tpe
      val parents =
        // lengths agree by construction — `mapClassDef` maps the list one for one — and a mismatch
        // means the traversal changed shape, in which case the MAPPED list is the honest answer
        // rather than a zip that silently truncates (see `spine` for the same reasoning).
        if orig.parents.sizeIs != mapped.parents.size then mapped.parents
        else orig.parents.zip(mapped.parents).map { (o, m) =>
          headSym(tpeOf(o)).filter(uninheritableSyms.contains) match
            case scala.None => m
            case Some(_)    =>
              val kept   = TirPrinter.tpe(tpeOf(o), TirPrinter.Style.canonical)
              val target = TirPrinter.tpe(tpeOf(m), TirPrinter.Style.canonical)
              seam("parent (implements)", target, kept, orig.origin, orig.symbol,
                   CollectionBoundaryCheck.Issue.InexpressibleParent)
              // …and a PORTER NOTE beside the class (§4.575). This is the shape that fact is worth
              // one for: a reader of `Sort.scala` sees one parent spelled in java where every other
              // mention of the same type is a `Tuple2`, and nothing in the emitted file or in the
              // diff against the upstream says why — the java `implements` clause is unchanged, so
              // the diff shows NOTHING at exactly the line the question is asked at.
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
        case (_, m)                               => m
      }
      mapped.copy(parents = parents, body = body)

  /** `StandardTraversal.mapSymbols`, minus the symbols the scope held back — so an excluded
    * declaration's SIGNATURE stays exactly as the frontend read it, which is what the restored tree
    * above says it is. The two must agree: a tree that says `java.util.List` over a symbol that
    * says `Buffer` is a lie every later reader believes. */
  private def mapSignatures(tbl: SymbolTable)(using p: Program): SymbolTable =
    if excluded.isEmpty then StandardTraversal.mapSymbols(this, tbl)
    // …and the same OWNERSHIP guard `mapSymbols` carries: an external member's signature is a fact
    // about a class file and this phase cannot move it. Without it the scoped path would retype
    // exactly the formals `coerce` and the boundary count now read (K15).
    else tbl.all.foldLeft(tbl) { (t, s) =>
      if excluded(s.id) || !p.owns(s.id) then t
      else t.updated(s.copy(info = StandardTraversal.mapType(this, s.info)))
    }

  /** DECISION PROVENANCE for the exclusion direction: one row per declaration the scope HELD BACK
    * and that the mapping would otherwise have moved.
    *
    * Without it the `Everywhere(except)` direction is invisible in `decisions.tsv` — the row that
    * would have been written simply is not there, and "why is this field a `java.util.List` when
    * every other file says `Buffer`" has no answer anywhere in the run (§5.1). `Reason.Configured`,
    * with the entry verbatim, because the fix is one line in that library's manifest.
    *
    * Only declarations (`Decision.isDeclaration`) and only ones a retyping would actually have
    * reached: a scope naming a package full of types with no collection in them is a policy the
    * port may well want and is not a decision about anything. */
  private def recordScopedOut(before: SymbolTable)(using p: Program): Unit =
    if excluded.isEmpty then return
    scope match
      case RuleScope.Everywhere(_) =>
        before.all.foreach { s =>
          if excluded(s.id) && collectionValued(p, s.info) && Decision.isDeclaration(p, s) then
            scope.entryFor(p, s).foreach { entry =>
              record(Decision(
                kind       = Decision.Kind.ScopedOut,
                subject    = s.id,
                subjectFqn = s.fullName,
                // NO `key` in `detail` — `Reason.Configured` below already carries the entry, and a
                // decider that spells it twice renders `key=… key=…` in the porter note.
                detail = Map(
                  "kept" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
                  "why"  -> ("this port's collections scope excludes this declaration, so it keeps " +
                    "the JDK type while the code around it moved to scala's"),
                ),
                reason = Reason.Configured(name, entry),
                origin = Decision.originOf(p, s.id),
              ))
            }
        }
      case RuleScope.Only(_) => () // the admitted half is recorded by `recordRetypings`

  /** DECISION PROVENANCE: one row per DECLARATION whose emitted SIGNATURE moved.
    *
    * Read from the phase's own before/after symbol tables rather than from `typeMap` — the same
    * discipline CLAUDE.md §4.56 states for the other direction ("a phase may only conclude
    * something about a type from what the PHASE ITSELF did to it"). An `info` that differs is the
    * definition of "this declaration's emitted signature moved", and it needs no second rule to
    * stay in step with the retyping as the map grows.
    *
    * `Decision.isDeclaration` drops parameters and method-locals: a method's `info` is a
    * `MethodType` carrying its parameter types, so a retyped parameter already moved the method's
    * row and a second row would restate it several thousand times on a library of this size.
    *
    * `Reason.Universal`, and that is a claim worth stating: which java type becomes which scala one
    * is fixed in this file, not taken as a constructor parameter, because it is a fact about the
    * two standard libraries — `java.util.List` is `mutable.Buffer` in every port there will ever
    * be. Should a library ever need its own mapping, the map becomes a parameter and this becomes
    * `Configured`; nothing else here changes.
    *
    * …with ONE exception, which is the scope. Under `RuleScope.Only` a declaration was retyped
    * because the port ASKED for it — directly, or by naming something this one flows from — so the
    * row's reason is `Configured(phase, entry)` with the entry verbatim (§4.575). Under every other
    * scope, including the default, retyping is the rule and `Universal` is the honest classification;
    * the reader's question there is not "who asked for this" but "why does java's collection have no
    * counterpart", which is the same answer for every port.
    */
  private def recordRetypings(before: SymbolTable, after: SymbolTable)(using p: Program): Unit =
    before.all.foreach { s =>
      after.get(s.id).foreach { now =>
        if now.info != s.info && Decision.isDeclaration(p, s) then
          val (reason, why) = admittedBy.get(s.id) match
            case Some(entry) =>
              (Reason.Configured(name, entry),
               "this port's collections scope admits this declaration (directly, or through a " +
                 "pure-move flow from something it names), and a signature that moves without its " +
                 "call sites is a compile error one call away")
            // …a RETARGET entry is a policy decision, so it may not read as the engine's own doing:
            // §4.45's rule is that a reader must be able to tell which repository the fix lives in,
            // and `Universal` here would send them to this file for a line in their manifest.
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
              // …and no `key`: where `admittedBy` supplied one, `reason` above is
              // `Reason.Configured(name, thatSameEntry)` and already carries it.
              "from" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
              "to"   -> TirPrinter.tpe(now.info, TirPrinter.Style.canonical),
              "why"  -> why,
            ),
            reason = reason,
            origin = Decision.originOf(p, s.id),
          ))
      }
    }

  /** which RETARGET entries this signature mentions, anywhere inside it — `Set.empty` when none,
    * which is every signature in a port that declares no retarget.
    *
    * Walked with [[StandardTraversal.mapType]] rather than a private recursion over `TypeRepr`'s
    * fourteen cases: CLAUDE.md §3's rule, and the reason for it is the same here as on trees — a
    * hand-rolled walk that stopped at `MethodType`'s parameters would answer "no retarget" for
    * every method in the program, silently, and every one of them would then be attributed to the
    * engine instead of to the manifest entry that caused it. */
  private def retargetKeysIn(t: TypeRepr)(using Program): Set[String] =
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
    case Some(rhs) => t.copy(rhs = Some(coerce(t.tpt.tpe, rhs, excluded(t.symbol))))
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
    case a: Tree.Assign =>
      // the TARGET may itself be a reference to a scoped-out declaration, in which case the slot is
      // a JDK one however the node reads — the same `actualOf` the argument side takes.
      val (want, wantScoped) = actualOf(a.lhs)
      a.copy(rhs = coerce(want, a.rhs, wantScoped))
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
    val out = copyConstructor(t2).orElse(capacityConstructor(t2)).orElse(staticRewrite(t2)).getOrElse {
      t2.fun match
        case Tree.Select(recv, m, _, so) => kindAt(recv).orElse(inheritedKind(recv, m)) match
          case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
          case None    => t2
        case _ => t2
    }
    // …and the seam arms see only what NOTHING ELSE REWROTE. Ordering them before the rewrites
    // reported `Collections.unmodifiableSet(mySet)` and `Collections.sort(myBuffer)` as unverifiable
    // external arguments while the very same run was retargeting both onto the runtime — eight
    // findings that were closed before they were written down, which is the report-credibility
    // failure §4.45 names: one such row teaches a reader that these findings need checking.
    val res =
      if out ne t2 then out
      else
        // …and on such a call the CONSUMER half of the seam is bridged where a live view exists,
        // BEFORE the count runs — a bridged slot is not a residue.
        val bridged = bridgeJavaFormals(t2)
        externalArgs(bridged)
        externalProducer(bridged)
    res match
      case a: Tree.Apply => noteImplicitReceiver(a); a
      case other         => other

  // -------------------------------------------------------------------------------------------
  // AN INHERITED COLLECTION CALL WITH NO RECEIVER WRITTEN — K5's family at an ANONYMOUS class
  // -------------------------------------------------------------------------------------------
  //
  // [[inheritedKind]] closed the class that EXTENDS a mapped JDK collection, and it is dispatched on
  // `Tree.Select(recv, m)` — it needs a receiver term both to ask the question and to build the
  // answer. Java's commonest way of writing such a call writes no receiver at all:
  //
  //   List<?> xs = new ArrayList<Object>() {{ add(a); add(b); }};      // the double-brace idiom
  //
  // Inside a NAMED class the frontend already supplies one — Spoon reports an implicit
  // `CtThisAccess` and `SpoonTir` emits `this.add(…)` / `Outer.this.add(…)`, choosing the innermost
  // enclosing type that PROVIDES the member — so those shapes have always reached the rewrite.
  // Inside an ANONYMOUS class the target is absent, the call is a bare `Tree.Ident`, and the whole
  // family went through untouched: `add(…)` against a `mutable.ArrayBuffer`, which has no such
  // member.
  //
  // WHERE THE RECEIVER COMES FROM. Java's rule is "the innermost enclosing class that provides the
  // member", and since the member is a mapped collection's, that is the innermost enclosing class
  // which IS one. The traversal is bottom-up, so every enclosing `new … { … }` is offered the calls
  // under it before anything further out is: it CLAIMS them when its own type answers [[kindAt]],
  // and DROPS them unclaimed when it does not — because `this` inside a nested anonymous class is
  // that class, and an anonymous class has no name to qualify with from inside one (T3), so there
  // is no receiver to synthesise. A dropped call is emitted exactly as java wrote it, which is the
  // honest refusal (M6) rather than a `this` naming the wrong instance.

  /** call sites recorded by [[transformApply]], awaiting an enclosing class that can supply `this`.
    *
    * Keyed by ORIGIN and not by node identity: `StandardTraversal.mapTerm` REBUILDS every node it
    * visits, so the `Tree.Apply` an enclosing hook sees is a copy of the one recorded here and no
    * identity survives. An origin is a java file, line and column, which is exactly one call site.
    * Cleared per translation in [[run]] — a phase instance is reused across source sets. */
  private val implicitPending = collection.mutable.Set[Origin]()

  /** [[inheritedKind]] with no receiver to read. The scope suppression `inheritedKind` applies is
    * about the RECEIVER's declaration, and there is no receiver here; the enclosing class's own
    * [[kindAt]] — which does go through `actualOf` — is what stands in for it at the claim. */
  private def implicitInheritedKind(m: SymId)(using p: Program): Option[Kind] =
    if mintedSyms.contains(m) then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  private def noteImplicitReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
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

  // -------------------------------------------------------------------------------------------
  // The EXTERNAL CALLEE seam — the boundary nothing could see, in both directions
  // -------------------------------------------------------------------------------------------
  //
  // CLAUDE.md §1(b) states the rule for a SCOPE seam: "a scope seam is also the one argument slot
  // with NO formal to compare against — the callee is then the JDK's own external symbol, which the
  // frontend interned without a signature". An external callee that DOES carry a signature is the
  // same fact one step out, and it is worse, because the signature is a fact about a COMPILED CLASS
  // FILE that no phase can move:
  //
  //   * an ANTLR parser's `ctx.atom()` really returns a `java.util.List<AtomContext>` — but
  //     `transformType` is position-blind, so the CALL NODE's type was retyped to `Buffer` and every
  //     reader downstream (the for-each, `coerce`, `CollectionBoundaryCheck`) believes it;
  //   * a generated lexer's constructor really takes a `java.util.Set<String>` — and `coerce` reads
  //     the formal THROUGH `remap`, so it sees `mutable.Set` on both sides and declines to bridge.
  //
  // BOTH SIDES READ THE SAME MOVED TYPE, so a check comparing node types reports ZERO on exactly
  // the seam the retyping made — which is what liqp measured: 15 errors at one third-party package
  // against 0 findings. The generalisation, and it is not about the JDK: EVERY RETYPING PHASE OWES
  // A BOUNDARY COUNT AT EXTERNAL CALLEES, NOT ONLY AT JDK ONES.
  //
  // What closes it is a conversion at the seam, and where none can be emitted the seam is COUNTED
  // and classified (§1) rather than approximated (M6). Both halves are below.

  /** A call to a method the PROGRAM DOES NOT DECLARE, whose declared result is a collection this
    * phase retypes — wrapped so the value really becomes what its node already claims.
    *
    * Fires only where every one of these holds, and each is the phase's own record rather than a
    * name test (CLAUDE.md §4.56):
    *
    *   - the callee is not owned by this program (`Program.owned`, structural);
    *   - the callee's OWNER is not itself a type in `typeMap`. That is what keeps this off the
    *     collection API's own members: `java.util.Map#keySet` is an external method returning
    *     `java.util.Set`, and its receiver has already been retyped, so its result already IS a
    *     scala set — wrapping it would be a second conversion of a value that never was java's;
    *   - the node's own TYPE is one this phase produced. This is the observable the seam has to be
    *     read from, and it is not the one this arm was first written against: **every external
    *     member the frontend interns carries `NoType`** — measured on liqp, 1157 external callees
    *     and not one with a `MethodType`, `java.lang.Object#toString` included. So there is no
    *     declared result type to read, and the node's `tpe` — which Spoon resolved and
    *     `transformType` then MOVED — is the only evidence that the value crossing this call is a
    *     collection. Reading it is still §4.56's question answered from the phase's own record: the
    *     node says `Buffer` precisely because THIS PHASE put it there;
    *   - that type is one `fromJava` can actually PRODUCE. `kindOf` holds every mapping TARGET —
    *     `ArrayBuffer`, `ArrayDeque`, `mutable.TreeMap`, `Tuple2` — while the helper is five
    *     overloads returning `Buffer`, `Set`, `Map`, `JavaIterator` and `JavaIterable`. Wrapping
    *     toward anything else emits a call whose result does not meet the node's own claim, and the
    *     error then names the HELPER rather than the boundary (`E134 None of the overloaded
    *     alternatives of method fromJava`), which is the worst shape this seam can produce. The test
    *     is [[CollectionsTransform.liveWrappable]] — the phase's own table read in the direction the
    *     phase moved it (§4.56) — and it subsumes the `JavaCollection` refusal, which is the same
    *     fact for the one target with no `scala.jdk` converter behind it: a shim built over a copied
    *     `Buffer` would detach both directions. Refused and counted rather than copied (M6);
    *   - the TYPE ARGUMENTS mention nothing this phase produced. `asScala` converts ONE level, so a
    *     `List<List<String>>` becomes `Buffer[java.util.List[String]]` while the retyping claims
    *     `Buffer[Buffer[String]]` — a wrap that silently lies one type argument in.
    *
    * The emitted call needs no evidence of WHICH java type it was: `fromJava` is overloaded and
    * scalac resolves it against the real static type from the class file, which is the one thing in
    * this whole seam that is not in doubt. The node keeps the type it already has, and for once
    * that is not a claim being made about a value — after the wrap the value really is that type
    * (ENGINE-LIMITS K6's first rule). */
  private def externalProducer(t: Tree.Apply)(using p: Program): Term =
    if fromJavaSym == SymId.None || !externalCallee(t.method) then t
    else headSym(t.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)) match
      case scala.None => t
      // …the pass-through arm is asked HERE and not at the top, so a call whose result is not a
      // collection at all never reaches it — and so a SUPPRESSION is a decision this phase made
      // about a value it would otherwise have wrapped, which is the only kind worth counting.
      case Some(_) if passesThrough(t) =>
        // a readable signature settles it and the guess is not consulted (see [[passesThrough]]);
        // an unreadable one leaves a REFUSAL RESTING ON A GUESS, which is its own residue and is
        // not the same fact as `externalArgs`' cannot-verify — that one is about a different slot
        // of the same call. Ordering the two on one lane would let a reader take either for the
        // other, which is the classification failure §4.45 is about.
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

  /** Does the PORT'S OWN VALUE simply pass through this external call?
    *
    * The node's type is the only evidence [[externalProducer]] has, and it is evidence of two
    * different things. Where the callee's result is a real `java.util.List`, the node says `Buffer`
    * because this phase MOVED it and the value is java's. Where the callee's result is a TYPE
    * VARIABLE, the node says `Buffer` because the caller HANDED IT ONE — `Objects.requireNonNull(m)`
    * and `ThreadLocal<Map<K,V>>.get()` both give back exactly what the port put in, already a scala
    * collection — and wrapping it converts a value that was never java's. Measured: 7 sites on liqp,
    * emitted as `fromJava(java.util.Objects.requireNonNull(aScalaMap))`, which is an E134 naming the
    * helper rather than the boundary.
    *
    * With no external signature there is no way to ask "is the result a type variable" — see K15 for
    * the `NoType` measurement — so the question is answered STRUCTURALLY, from the call itself: the
    * value passes through iff the result type ALREADY OCCURS on the input side, as an argument's
    * type or anywhere inside the receiver's. That is what a generic pass-through IS, and it costs
    * the honest cases nothing: `ctx.atom()`'s receiver is a parse-tree context that mentions no
    * collection, and `ServiceLoader<T>.iterator()`'s receiver mentions `T` but not the `JavaIterator`
    * its result became.
    *
    * ==…but the guess is CONSULTED LAST, because it is also the shape of an honest utility==
    * "The result type occurs on the input side" is equally the shape of every non-identity
    * `List`→`List` third party — `reverse`, `sorted`, a cache's `getOrDefault` — where the value
    * crossing the call really IS java's; and of every concrete-returning member of a generic holder
    * instantiated at a collection (`Holder<List<String>>.names()`), where the RECEIVER carries the
    * occurrence and nothing bridges a receiver. Suppressing there is a wrap not emitted at a real
    * seam, and — because the suppression was an EARLY EXIT — a seam not counted either, which is the
    * pre-K15 state at the very calls K15 exists for.
    *
    * So the CLASS FILE is asked first, wherever it can be read. A `MethodType` is all-or-none
    * (`ExternalSignatureSpec`), so a member whose result is a type VARIABLE is signature-less by
    * construction — which means a READABLE result whose HEAD is a type this phase maps is a real
    * java collection, whatever the argument and receiver types happen to be. That is the phase's own
    * table answering the question (§4.56), and it leaves the guess exactly the calls K15 measured it
    * on: the ones with no signature to read. Those are still suppressed — and now COUNTED, in a lane
    * of their own (see [[externalProducer]]). */
  private def passesThrough(t: Tree.Apply)(using p: Program): Boolean =
    !declaredResultIsMapped(t) && {
      val want = t.tpe
      want != TypeRepr.NoType && (
        t.args.exists(_.tpe == want) || (t.fun match
          case Tree.Select(recv, _, _, _) => occursIn(want, recv.tpe)
          case _                          => false))
    }

  /** does the CLASS FILE declare this callee's result to be a collection the mapping covers?
    *
    * Read LITERALLY and never through `remap`: an unowned symbol's signature is a fact about a
    * compiled class file, which `StandardTraversal.mapSymbols` deliberately does not move (§4.56),
    * so the head read here is still java's own name. `None` — no signature at all — is not evidence
    * of anything and answers `false`, which is what leaves the structural guess in charge. */
  private def declaredResultIsMapped(t: Tree.Apply)(using p: Program): Boolean =
    declaredResult(t).flatMap(headSym).flatMap(p.symbolOf).exists(s => typeMap.contains(s.fullName))

  /** the callee's DECLARED result type, where the class file could be read for one. */
  private def declaredResult(t: Tree.Apply)(using p: Program): Option[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(_, ret, _)                       => ret
      case TypeRepr.PolyType(_, TypeRepr.MethodType(_, ret, _)) => ret
    }

  /** could the callee's class file be read for a signature at all? The two answers a suppression has
    * to be told apart by: a refusal the CLASS FILE licensed, and one resting on a GUESS. */
  private def signatureReadable(t: Tree.Apply)(using p: Program): Boolean =
    p.symbolOf(t.method).exists(_.info != TypeRepr.NoType)

  /** does `needle` occur anywhere inside `hay`, as a whole type? Structural equality, and the
    * traversal is [[StandardTraversal.mapType]]'s for CLAUDE.md §3's reason. */
  private def occursIn(needle: TypeRepr, hay: TypeRepr)(using Program): Boolean =
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

  /** is this a method the PROGRAM DOES NOT DECLARE, and not one of the collection API's own?
    *
    * Three exclusions, each of which would otherwise make the seam arms fire on a value that never
    * was java's: a symbol THIS PHASE MINTED (every rewrite's target — `+=`, `filtered`, `fromJava`
    * itself), and a callee whose OWNER is either a type the mapping covers or one of its targets.
    * `java.util.Map#keySet` is the shape that matters: an external method returning
    * `java.util.Set`, on a receiver this phase already retyped, so its value IS a scala set and
    * wrapping it would convert something that was never java's. */
  private def externalCallee(m: SymId)(using p: Program): Boolean =
    m != SymId.None && !ownedSym(m) && !mintedSyms.contains(m) &&
      // …and it must have an OWNER. A symbol with none is not a member of any class file: it is an
      // operator or an intrinsic the frontend interned bare (`scala.<op>#+`), and `"…" + aMap` was
      // reported twice as an unverifiable external argument because nothing asked. There is no
      // class file behind it to be unable to read, which is what this whole family is about.
      p.symbolOf(m).exists(_.owner != SymId.None) &&
      !p.symbolOf(m).flatMap(c => p.symbolOf(c.owner))
        .exists(o => typeMap.contains(o.fullName) || retypedTargets.contains(o.fullName)) &&
      // …and never a member THIS PHASE'S OWN TABLES cover. `staticRewrite` returning `None` means
      // one of two things — no arm matched, or an arm REFUSED — and only the first is an external
      // seam. Measured: `Arrays.asList(arr)` is K6.5's deliberate refusal, kept under the JDK's own
      // name so the error reads as an untranslated call; wrapped here it read as a translated one
      // and failed a type further in. A refusal that the next mechanism paints over is a refusal
      // nobody can find (M6).
      !handledStatic(m)

  /** does this callee name a member one of THIS PHASE'S OWN static arms covers?
    *
    * The phase's record of itself, in `MemberKey` form (§4.56). Two callers read it and they are
    * the same question asked at two seams: a call still standing at such a name is a call this
    * phase DECLINED to rewrite — every arm that fired left its minted helper's symbol behind
    * instead — so its value is whatever java's was, whatever the node's retyped `tpe` now says. */
  private def handledStatic(m: SymId)(using p: Program): Boolean =
    p.symbolOf(m).flatMap(c => p.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))
      .exists(CollectionsTransform.handledStatics.contains)

  /** the source half of the same fact — a value PRODUCED by a call this phase refused to rewrite.
    *
    * `Insertions.of(Arrays.asList(arr))` is the measured shape. The `asList` is K6.5's aliasing
    * refusal, so the emitted text keeps the JDK name and the value really is a `java.util.List`;
    * the NODE says `Buffer`, because `transformType` is position-blind and moved the type on both
    * sides of that call. Read from the node alone, [[coerce]] therefore sees a `Kind.Seq` meeting a
    * shim-typed formal, finds a factory on its table's first line, and emits
    * `JavaCollection.from(java.util.Arrays.asList(…))` — a factory handed a java collection. Same
    * error count either way (the call could not compile before) and a strictly worse message: it
    * names the WRAPPER instead of the boundary a reader has to act on, which is the shape
    * `CLAUDE.md` §1(b) warns about and `ENGINE-LIMITS.md` K2.5 left open.
    *
    * This is exactly [[isKeySetView]]'s rule at a second site — "the recorded type is not a witness
    * of what the emitter will print" — and it is answered from the phase's own tables rather than
    * from an arm-by-arm list, so a refusal added later is covered by construction. */
  private def refusedRewriteSource(t: Term)(using Program): Boolean = t match
    case a: Tree.Apply => handledStatic(a.method)
    case _             => false

  /** does this type mention, anywhere inside its ARGUMENTS, a type this phase PRODUCED?
    *
    * The retyped direction, because by the time this runs the node has already been mapped: a
    * `java.util.List<java.util.List<String>>` reads `Buffer[Buffer[String]]` here, and the inner
    * `Buffer` is the evidence that a one-level `asScala` would leave a `java.util.List` where the
    * type says otherwise.
    *
    * Walked with [[StandardTraversal.mapType]] and never a private recursion over `TypeRepr`'s
    * cases, for CLAUDE.md §3's reason: a hand-rolled walk that stopped one constructor short would
    * answer "no nesting" for the shape this test exists to catch, and the wrap would then be
    * emitted for exactly the type it must refuse. The HEAD is excluded by construction — the caller
    * has already established it, which is the trigger, not the problem. */
  private def mentionsRetyped(t: TypeRepr)(using p: Program): Boolean = t match
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

  /** The CONSUMER half of the same seam, for the calls where it can only ever be COUNTED — and the
    * split between this and [[CollectionBoundaryCheck]] is drawn on the one line that matters.
    *
    * An argument the phase retyped, handed to a method the program does not declare. Whether it
    * fits is decided by a FORMAL in a class file. That used to be unanswerable for every such
    * call: the frontend interned every external member with `NoType` — 1157 on one library, not
    * one `MethodType` — so `wrapIterableArgs` saw no formals (its `formals.sizeIs != t.args.size`
    * guard declines at 0), `coerce` was never reached, and `CollectionBoundaryCheck`'s argument arm
    * skipped the call for the same reason. Measured then: 15 compile errors at one third-party
    * package against 0 findings.
    *
    * `SpoonTir` now records what a class file can be read for scope-free, so the two halves have
    * different owners:
    *
    *   - **the formal is READABLE** — then `CollectionBoundaryCheck` can see the slot for itself,
    *     with both types in hand, and classifies it properly (a `java.util.stream.Stream` formal is
    *     `UntranslatedFamily`, a mapped one at a class file is `ExternalCallee`). Counting it HERE
    *     as well would put two rows on one seam;
    *   - **there is NO signature** — a class file the parse could only partially resolve. The check
    *     has nothing to compare and skips the call entirely, so this is the arm that keeps it
    *     visible: a CANNOT-VERIFY count, saying so, because where the formal really is `Object` the
    *     retyped value conforms and where it is a `java.util.*` the port does not compile, and
    *     nothing in the pipeline can tell those apart.
    *
    * The reason this may not simply stop counting where a signature appeared is CLAUDE.md §1(b)'s:
    * a check that reads zero because it stopped looking is worse than one that reads high. It does
    * not read zero — the row moved to the check that can now say more about it. */
  private def externalArgs(t: Tree.Apply)(using p: Program): Unit =
    if externalCallee(t.method) && p.symbolOf(t.method).forall(_.info == TypeRepr.NoType) then
      t.args.foreach { a =>
        headSym(a.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)).foreach { _ =>
          seam("argument (external callee, no signature)", "unknown — the callee is a class file",
               TirPrinter.tpe(a.tpe, TirPrinter.Style.canonical), a.origin, t.method)
        }
      }

  /** record one external seam this phase could not close, for [[boundary]] to report. A refusal
    * that is not counted is indistinguishable from a seam that does not exist (M6). */
  private def seam(slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId,
                   issue: CollectionBoundaryCheck.Issue = CollectionBoundaryCheck.Issue.ExternalCallee): Unit =
    externalSeams += CollectionBoundaryCheck.Finding(issue, slot, expected, actual, origin, enclosing)

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

  /** Java's CAPACITY-HINT constructor at a HASHED collection — `new HashMap<>(16)`,
    * `new HashSet<>(n)`.
    *
    * [[copyConstructor]]'s note says a capacity hint "maps correctly by accident", and for the
    * SEQUENCE targets it does: `new ArrayBuffer(10)` means what `new ArrayList<>(10)` means. It is
    * false for the HASHED ones, and silently so — scala's `mutable.HashMap` declares `()` and
    * `(initialCapacity: Int, loadFactor: Double)` and nothing in between, so the one-argument java
    * form lands on no overload at all:
    *
    * {{{ None of the overloaded alternatives of constructor HashMap … match arguments ((n : Int)) }}}
    *
    * Java's own one-argument constructor is `(initialCapacity, DEFAULT_LOAD_FACTOR)` with
    * `DEFAULT_LOAD_FACTOR = 0.75f`, and scala's companion publishes exactly that value as
    * `defaultLoadFactor` — so supplying it is java's own definition rather than a guess, which is
    * what makes this a translation and not an approximation (M6).
    *
    * The two arms are DISJOINT by construction: [[copyConstructor]] takes the single-collection
    * argument, this one takes a single `scala.Int`, and java's `HashMap`/`HashSet` have no other
    * one-argument constructor. A two-argument `(int, float)` needs nothing — scala widens the
    * `Float` to the `Double` the second parameter asks for. */
  private def capacityConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val isInt = t.args match
        case List(a) => headSym(a.tpe).flatMap(summon[Program].symbolOf).exists(_.fullName == "scala.Int")
        case _       => false
      for
        tgt <- headSym(n.tpe)
        lf  <- loadFactorSyms.get(tgt) if isInt
      yield Tree.Apply(t.fun, t.args :+ Tree.Ident(lf, TypeRepr.NoType, t.origin), t.method, t.tpe, t.origin)
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
    yield MemberKey(o.fullName, m.name).render
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
      case (Some("java.util.Collections#swap"), List(xs, i, j))   => Some(factory(sym("swap"), List(xs, i, j)))
      case (Some("java.util.Collections#shuffle"), List(xs, rnd))  => Some(factory(sym("shuffle"), List(xs, rnd)))
      // The IMMUTABLE PRODUCERS. These are the other direction of the retype: not an argument the
      // port hands the JDK, but a value the JDK hands BACK, at a slot this phase already moved —
      // `Found: java.util.Map[K, V] / Required: mutable.Map[String, Insertion]`. Nothing coerces
      // them, and nothing can: the JDK object is not a scala collection, so the rewrite has to
      // produce the scala value in the first place.
      //
      // Their targets REPRODUCE java's immutability rather than dropping it — see the helpers.
      // `mutable.ArrayBuffer.empty` would compile and turn a loud `UnsupportedOperationException`
      // into a silent write to whatever shared constant the factory's result was stored in.
      case (Some("java.util.Collections#emptyList"), Nil)           => Some(factory(sym("emptyList"), Nil))
      case (Some("java.util.Collections#emptyMap"), Nil)            => Some(factory(sym("emptyMap"), Nil))
      case (Some("java.util.Collections#emptySet"), Nil)            => Some(factory(sym("emptySet"), Nil))
      case (Some("java.util.Collections#singletonList"), List(x))   => Some(factory(sym("singletonList"), List(x)))
      case (Some("java.util.Collections#singleton"), List(x))       => Some(factory(sym("singleton"), List(x)))
      case (Some("java.util.Collections#singletonMap"), List(k, v)) => Some(factory(sym("singletonMap"), List(k, v)))
      // …and the unmodifiable VIEWS, which are the same family with a live underlying collection.
      // ENGINE-LIMITS K6 recorded these as unmappable and they were, while the only candidate
      // targets were the STDLIB's — scala has no read-only `Buffer`/`Set`/`Map` view, so the shapes
      // available were a copy (detaches the view) and the identity (drops the immutability). The
      // runtime's `Frozen*` delegate every READ to the collection they wrap, which is java's answer.
      case (Some("java.util.Collections#unmodifiableList"), List(c)) => Some(factory(sym("unmodifiableList"), List(c)))
      case (Some("java.util.Collections#unmodifiableSet"), List(c))  => Some(factory(sym("unmodifiableSet"), List(c)))
      case (Some("java.util.Collections#unmodifiableMap"), List(c))  => Some(factory(sym("unmodifiableMap"), List(c)))
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
      // `toSet` and `toMap` were K6's two openly-unmapped collectors, and the reason they could not
      // ride on `toList`'s arm is that `toList` collapses to NOTHING — the receiver already IS the
      // sequence — while these two change the TARGET TYPE. Each therefore needs a helper of its
      // own, and neither may be guessed: java's two-argument `toMap` THROWS on a duplicate key
      // where a scala `.toMap` over pairs silently keeps the last (§4.4). See the helpers.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toSet") =>
        recv.map(r => factory(sym("toSet"), List(r)))
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toMap") =>
        // the collector carries its mappers INSIDE it, exactly as `toCollection` carries its
        // factory, so the collapse cannot end at the receiver: they move to the helper's argument
        // list. Java has a two- and a three-argument form and nothing else; an arity this does not
        // recognise declines the rewrite rather than dropping an argument.
        val fs = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if fs.sizeIs != 2 && fs.sizeIs != 3 then None else recv.map(r => factory(sym("toMap"), r :: fs))
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
    * ==…and the pack has TWO node shapes, because the callee here is EXTERNAL==
    * The frontend materialises the pack as a `Tree.NewArray` only for a callee this program
    * DECLARES. At an EXTERNAL one — which `java.util.Arrays.asList` is, and which is the only kind
    * this rewrite ever sees — it mints a `Tree.Repeated`, since scalac reads a java `T...` in a
    * class file as a REPEATED parameter and a materialised pack there is one argument too many
    * (`ENGINE-LIMITS.md` K6.5). A `Repeated` is the argument list's TAIL, already opened.
    *
    * Both shapes therefore mean "these are the ELEMENTS", and both must open, or the two halves of
    * one decision disagree: read as an ordinary argument the `Repeated` carries an ARRAY node type,
    * so it fell into the aliasing arm below and REFUSED a pack it had itself just opened —
    * `asList(xs, xs)` and `asList(s)` emitted `java.util.Arrays.asList(…)` under the retyped return
    * type, which cannot compile, while `asList(1, 2, 3)` (never packed at all) was rewritten. Each
    * half was green alone; the composition was not.
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
      // the EXTERNAL-callee shape of the same pack — opened, never read as one array argument.
      case init :+ Tree.Repeated(elems, _, _)               => Some(init ++ elems)
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

  /** A `stream()` receiver AS the scala sequence the collapse consumes — the head of the chain.
    *
    * This used to be an unconditional `Tree.Select(r, asScalaBuffer)`, and that is a table lookup
    * keyed on ONE kind applied to every kind. `asScalaBuffer` is an extension in `JavaCollection`'s
    * companion — the SHIM's accessor — and only a receiver that IS (or extends) the shim has it.
    * Measured on liqp: `value asScalaBuffer is not a member of scala.collection.mutable.Buffer[…]`
    * where the declaration was a `java.util.List`, and `… is not a member of
    * scala.collection.mutable.Map[…]` where it was a `Map` reached through this phase's own
    * `entrySet()` rewrite (which returns the map). Three sites, none of which any check could see:
    * the collapse fired, so nothing reported an untranslated chain.
    *
    * What the receiver really is decides, in this order and no other:
    *
    *   - a type this phase MINTED (`kindOf`/`shimSyms` — §4.56's "what did the phase do to it");
    *   - failing that, the TARGET of the type that DECLARES `stream()`. That is the case a library
    *     which defines its own collection is made of: `class Own extends AbstractCollection<T>`
    *     keeps its own type, which this phase never minted, and `java.util.Collection#stream`'s
    *     owner maps to the shim — so `own.asScalaBuffer` is right, and it is right BECAUSE `Own`
    *     really does extend `JavaCollection` after the retyping.
    *
    * A `Kind.Set` or `Kind.Map` source is `.toBuffer` — a COPY, on the same footing the collapse
    * already accepts for `asScalaBuffer` (its own note: java's stream is lazy, the chain's terminal
    * materialises, and the observable result is identical). It is not optional: everything the
    * chain collapses onto (`JavaCollection.filtered`, `sortedWith`, `into`) is declared over a
    * `Buffer`, and a scala `Map[K, V]` copied to a `Buffer` is `Buffer[(K, V)]` — precisely the
    * `entrySet()` view java streamed. */
  private def streamSource(r: Term, m: SymId)(using p: Program): Term =
    val effective = headSym(r.tpe)
      .filter(s => kindOf.contains(s) || shimSyms.contains(s))
      .orElse(p.symbolOf(m).flatMap(x => p.symbolOf(x.owner)).flatMap(o => remap.get(o.id)))
    effective match
      case Some(s) if shimSyms.contains(s) && asScalaBufferSym != SymId.None =>
        Tree.Select(r, asScalaBufferSym, asBuffer(r.tpe), r.origin)
      case Some(s) if kindOf.get(s).contains(Kind.Seq)                       => r
      case Some(s) if kindOf.get(s).contains(Kind.Set) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, asBuffer(r.tpe), r.origin)
      // a `Map[K, V]` copies to a `Buffer[(K, V)]`, so the ARITY changes and `asBuffer` — which
      // only swaps the head — would claim a `Buffer[K, V]`. The bare constructor is the honest
      // record of a type this phase cannot spell precisely, and only its head is ever read.
      case Some(s) if kindOf.get(s).contains(Kind.Map) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym), r.origin)
      case _ => r

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
    * Both obvious repairs are measured dead ends (ENGINE-LIMITS.md K2): a `given Conversion`
    * never fires, because scala does not look for one when no OVERLOAD alternative matches; and
    * widening the parameter to `scala.collection.Iterable` breaks the bodies that iterate-and-
    * REMOVE through it. Wrapping the ARGUMENT has neither problem — the type is exact before
    * overload resolution runs, and the parameter keeps the capability it declares. */
  private def wrapIterableArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    // ONLY A CALLEE THE PROGRAM OWNS, and that is not a narrowing — it is what this pass IS.
    //
    // The wrap it inserts targets a SHIM, and a shim formal can only ever belong to a declaration
    // the port EMITS: no class file names `balticporter.runtime.JavaIterable`. Reading an EXTERNAL
    // formal through `remap` claims otherwise — it says a `java.util.Collection` slot wants
    // `JavaCollection` — and produces two failures at once. `String.join(",", JavaIterable.from(xs))`
    // hands a standalone runtime trait to a class file asking for `java.lang.Iterable`, so the seam
    // moves one type to the left and stops being findable; and, worse, the wrap lands on a call the
    // phase is ABOUT TO REWRITE — `this.items.addAll(other.items)` became
    // `this.items ++= JavaCollection.from(other.items)`, where `++=` wants an `IterableOnce` and
    // the shim is not one. Measured at 4 errors on a port that had 0, with 8 member digests moved
    // and every check count flat: nothing but the compiler could see it.
    //
    // The java-formal direction is [[bridgeJavaFormals]]'s, and it runs AFTER the rewrites for
    // exactly the reason the second failure above gives.
    //
    // AND IT IS NOT GATED ON `javaIterableSym`. It was, and that gate was a fact about a DIFFERENT
    // shim: `javaIterableSym` exists only where the program NAMES `java.lang.Iterable`, while the
    // table in [[coerce]] has two independent targets and the `JavaCollection` half has nothing to
    // do with `Iterable`. A library that uses `Collection` throughout and never mentions `Iterable`
    // — liqp, 135 files — therefore had this whole pass switched off, silently: no check fires, no
    // policy entry goes unmatched, and the only evidence is `E134 None of the overloaded
    // alternatives` at each call where java's own `List`-is-a-`Collection` subtyping did not
    // survive the retyping. §4.56's rule at one remove: "is there a `JavaIterable` in this program"
    // is not a fact about a `Collection`-typed formal. `coerce` already returns the argument
    // untouched when no factory matches, so the gate bought nothing but the bug — what it looked
    // like it was protecting is now protected where it belongs, at the target comparisons.
    if !ownedSym(t.method) || keepsJavaFormals(t) then t
    else
      val formals = formalsOf(t)
      if formals.sizeIs != t.args.size then t
      else
        val as = t.args.zip(formals).map((a, f) => coerce(f, a))
        if as == t.args then t else t.copy(args = as)

  /** Does this call's callee keep JAVA formals — i.e. is its signature one this phase did not and
    * cannot move? Three cases, and the third is the one that decides the shape.
    *
    *   - the CALLEE is a declaration this run's scope held back, so its parameters stayed;
    *   - the RECEIVER resolves through a held-back declaration to a java collection, so the call
    *     binds to the JDK's own API whatever the node types say (`b.raw.addAll(mine)`);
    *   - the callee is a genuine EXTERNAL seam, by [[externalCallee]]'s own four exclusions.
    *
    * "not owned" alone is NOT the test, and the difference is a refusal being painted over. A
    * `super.putAll(m)` inside a class extending a mapped collection is an unowned callee with a
    * `java.util.Map` formal — and this phase REFUSED to rewrite it (the blanket `super` guard),
    * because every scala-shaped form of it is an E040. Bridging its argument leaves the same
    * uncompilable call with a wrapper inside it, so the error stops naming the member and starts
    * naming the helper: M6's refusal made unfindable, which is the failure K6.5 records under its
    * own name. `externalCallee` already excludes a callee whose OWNER this phase maps, for exactly
    * that reason. */
  private def keepsJavaFormals(t: Tree.Apply)(using Program): Boolean =
    excluded(t.method) || externalCallee(t.method) || (t.fun match
      case Tree.Select(recv, _, _, _) => actualOf(recv)._2
      case _                          => false)

  /** the callee's declared formals, or `Nil` where it has none. */
  private def formalsOf(t: Tree.Apply)(using p: Program): List[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }.getOrElse(Nil)

  /** The CONSUMER half of the external seam — a value this phase retyped, at a formal it did not
    * and cannot: a class file's (`ENGINE-LIMITS.md` K15) or a declaration this run's scope held
    * back. Bridged with a LIVE `JavaCollections.toJava` view.
    *
    * ==Why this does NOT live in `wrapIterableArgs`, which is the obvious home==
    * That pass runs BEFORE the call rewrites, which is right for its own job: a shim-typed formal
    * belongs to a declaration the port emits, and the wrap has to be in place before overload
    * resolution ever sees the argument. It is wrong for this one. A `java.util.*` formal is the
    * signature of a method the phase may be about to RETARGET — `Collections.sort(myBuffer)` goes
    * to `JavaCollections.sort`, whose parameter is a `Buffer`, and `items.addAll(more)` becomes
    * `items ++= more` — so bridging first hands the rewritten call a wrapped argument its new
    * target does not want. Measured at 8 specs the first time the two were merged.
    *
    * So it runs where the seam COUNT runs, on a call nothing else rewrote, which is the same
    * ordering rule K15 already records for the count and for the same reason. */
  private def bridgeJavaFormals(t: Tree.Apply)(using p: Program): Tree.Apply =
    if !keepsJavaFormals(t) then t
    else
      val formals = formalsOf(t)
      if formals.sizeIs != t.args.size then t
      else
        // …and the UNIVERSAL formal is a bridge only at a CLASS FILE's slot, which is the one of
        // `keepsJavaFormals`' three cases where the code on the other side really is java. A
        // scoped-out declaration's `Object` formal belongs to scala this port EMITS, and a held-back
        // declaration is held back so that its own body keeps working — bridging into it would hand
        // a ported method a `java.util.List` its body no longer expects.
        val external = externalCallee(t.method)
        val as = t.args.zip(formals).map((a, f) =>
          coerce(f, a, expectedScoped = true, expectedExternal = external))
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
  private def coerce(expected: TypeRepr, actual: Term, expectedScoped: Boolean = false,
                     expectedExternal: Boolean = false)(using p: Program): Term =
    // the symbol table is retyped AFTER the trees (see `run`), so a formal read here is still the
    // ORIGINAL java symbol — `java.lang.Iterable`, not the shim. Compare through `remap`, which
    // makes this correct on either side of that pass.
    //
    // …EXCEPT for a declaration this run's scope held back, on either side. That one is not
    // "written in java's namespace and about to move"; it is staying, so reading it through `remap`
    // would claim a slot wants a shim it will never have (or that a value is a `Buffer` when the
    // emitted code says `java.util.List`) and wrap on both counts. A scoped-out side is therefore
    // taken LITERALLY, no factory matches it, and the seam is left for `CollectionBoundaryCheck`
    // to count as `Issue.ScopedOut` — which is the honest answer, since no wrap can close it.
    def scalaSym(x: SymId, scoped: Boolean): SymId = if scoped then x else remap.getOrElse(x, x)
    val (actualT, actualScoped) = actualOf(actual)
    val wants = headSym(expected).map(scalaSym(_, expectedScoped))
    val got   = headSym(actualT).map(scalaSym(_, actualScoped))
    val from  = got.filterNot(shimSyms.contains).flatMap(kindOf.get)
    // …and the slot that is LITERALLY a java collection — the seam `ENGINE-LIMITS.md` K15's
    // consumer half is about. `expectedScoped` means the expected side is taken as it is written
    // rather than through `remap`, which is true of exactly two things: a declaration this run's
    // scope held back, and an EXTERNAL callee's formal, whose signature lives in a class file no
    // phase can move. In both, a value this phase retyped is meeting a `java.util.*` that stayed —
    // so the wrap goes the other way, and it goes through the phase's OWN record of what it maps
    // (§4.56) rather than any test on the name.
    val wantsJava = expectedScoped &&
      wants.flatMap(p.symbolOf).exists(o => typeMap.contains(o.fullName))
    // …and the slot with NO type error behind it, which is why nothing was looking for it. A class
    // file's `java.lang.Object` formal takes anything, so a retyped collection conforms and the port
    // compiles — while the callee is reflective third-party code that java handed a `HashMap` and
    // this port hands a `mutable.Map`: `toString`, `instanceof` and every serializer see something
    // else (`CollectionsTransform.ObjectFqn` for why naming it is not §4.56's name test). `toJava`
    // is the FAITHFUL answer and not a compromise — java's value at that slot really WAS a java
    // collection — which is what licenses inserting a wrap where nothing is broken. EXTERNAL only:
    // a held-back declaration's `Object` formal belongs to scala this port emits.
    val wantsUniversal = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    // …asked so that an ABSENT shim can never match. A shim symbol is `SymId.None` when nothing in
    // the program maps to it (`javaIterableSym` exists only where something names
    // `java.lang.Iterable`), and a `wants` of `Some(SymId.None)` — an expected type whose head did
    // not resolve — would then satisfy `contains` and wrap a value in a factory for a type this run
    // does not have. That is the real hazard the pass-level `javaIterableSym` gate was standing in
    // front of, and it belongs here, per target, where it costs no OTHER target its bridge.
    def wantsIs(s: SymId) = s != SymId.None && wants.contains(s)
    val factory = from match
      case _ if wants.isEmpty || isKeySetView(actual) || refusedRewriteSource(actual) => SymId.None
      case Some(Kind.Seq | Kind.Set | Kind.Map) if wantsIs(javaIterableSym)    => iterableFromSym
      case Some(Kind.Seq)                       if wantsIs(javaCollectionSym)  => collectionFromSym
      case Some(Kind.Set)                       if wantsIs(javaCollectionSym)  => collectionFromSetSym
      // `asJava` converts ONE level, exactly as `asScala` does, so a `Buffer[Buffer[String]]` at a
      // `java.util.List<java.util.List<String>>` formal would emit a wrap that lies one type
      // argument in. Refused and counted, the same way [[externalProducer]] refuses the mirror.
      case Some(Kind.Seq | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal) && mentionsRetyped(actualT)                    => SymId.None
      case Some(Kind.Seq | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal) && toJavaSym != SymId.None                     => toJavaSym
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
    /** is the receiver `super`? Scala admits `super` in exactly ONE position — as the qualifier of
      * a member selection — and three of the shapes below put it somewhere else: `entrySet` returns
      * the receiver ALONE (`for (e <- super)`), the `Seq` `get` makes it a function
      * (`super(i)`), and every `+=`/`-=`/`++=` renders INFIX (`super ++= m`). All three are E040
      * SYNTAX errors, and a syntax error is strictly worse than the type error they replace: it
      * cannot be attributed to a member and it can take the rest of the file with it.
      *
      * So this is a BLANKET refusal with no exceptions, for the reason `onShim` above is one: the
      * arms that WOULD survive (`super.getOrElse(k, d)`, `super.contains(k)`) are not worth the
      * next arm that will not, and "which of these renders infix" is a fact about the EMITTER that
      * this phase cannot read. `super.get(k)` therefore stays untranslated and fails to compile
      * naming the member, which is a counted refusal (ENGINE-LIMITS M6) rather than a broken file.
      * Note this costs nothing that worked before: a `super` receiver reaches `rewrite` at all only
      * through `inheritedKind`, which is new. */
    val onSuper = recv.isInstanceOf[Tree.Super]
    (name, t.args, k) match
      case _ if onSuper => None
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
      // A WILDCARD-typed map is java's three `Object`-keyed members and nothing else — see
      // [[wildcardMapCall]] for why the ordinary rewrite cannot be used there.
      case (n, List(key), Kind.Map) if wildcardMapCall(n, recv) =>
        Some(staticCall(wildcardMapSym(n), List(recv, keyArg(key, recv)), t, so))
      case ("get", List(key), Kind.Map)         => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(nullOf(so), recv, so)), t, so))
      case ("getOrDefault", List(key, d), _)    => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(d, recv, so)), t, so))
      case ("set", List(i, x), Kind.Seq)        => Some(call(recv, updateSym, List(i, x), t, so)) // xs(i) = x
      // Java's `Map.put` RETURNS THE PREVIOUS VALUE; scala's `update` returns `Unit`. Mapping to
      // `update` discarded it at every site — `if (map.put(k, v) != null)` became a comparison
      // against `Unit`. Scala's own `put` keeps it, as an `Option`, so `getOrElse(null)` restores
      // java's contract exactly. The default is ascribed to `V`, as `get`'s is.
      case ("put", List(key, v), Kind.Map)      =>
        Some(call(call(recv, putSym, List(keyArg(key, recv), v), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // likewise `Map.remove`, which returns the value that was there.
      case ("remove", List(key), Kind.Map)      =>
        Some(call(call(recv, removeSym, List(keyArg(key, recv)), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
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
      // ---- `Collection.toArray()` and `toArray(T[])` ----
      //
      // Neither has a scala counterpart with java's meaning, and left alone BOTH bind to something
      // that is not even a `toArray`: scala's is PARENLESS, so `xs.toArray()` parses as
      // `xs.toArray.apply()` — an Array INDEX — and the compiler says `missing argument for
      // parameter i of method apply in class Array`, an error naming neither collections nor
      // `toArray`. The one-argument form is the same misparse with the array in the index slot
      // (`Found: Array[Object] / Required: Int`).
      //
      // The rewrite is a `JavaCollections` helper for each, and the reason is java's CONTRACT rather
      // than the arity: `toArray()` allocates `Object[]` where scala's `toArray` allocates on the
      // element's class, and `toArray(T[])` fills the caller's array when it fits, allocates on its
      // RUNTIME component type when it does not, and writes a null terminator. All three are
      // CLAUDE.md §4.4 shapes — `xs.toArray` compiles and silently does none of them. See the
      // helpers' own docs, which are where the contract is stated.
      case ("toArray", Nil, Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv),
                        sym("toArray"), t.tpe, t.origin))
      case ("toArray", List(a), Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv, arrayArg(a, t)),
                        sym("toArray"), t.tpe, t.origin))
      // `subList` is a WRITE-THROUGH VIEW and `slice` is a copy — `list.subList(a, b).clear()`
      // removes the range from the list in java and does nothing to a copy (§4.4). `putIfAbsent`
      // returns the PREVIOUS value, so `null` is what a successful insertion returns, which is the
      // opposite of what `getOrElseUpdate` hands back. Both are in the helper for the same reason
      // `removeValue` is: scala has the operation and it means something else.
      case ("subList", List(a, b), Kind.Seq) if sym("subList") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("subList"), TypeRepr.NoType, so), List(recv, a, b),
                        sym("subList"), t.tpe, t.origin))
      case ("putIfAbsent", List(key, v), Kind.Map) if sym("putIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("putIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), v), sym("putIfAbsent"), t.tpe, t.origin))
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
      case ("containsKey", List(key), Kind.Map) => Some(call(recv, containsSym, List(keyArg(key, recv)), t, so))
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

  /** `JavaCollections.member(args)` — a runtime helper, typed as what the java call it replaces
    * was recorded at (ENGINE-LIMITS K6's first rule: a node describes what it emits). */
  private def staticCall(member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Ident(member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** Is this a call on a map whose type arguments are WILDCARDS, at one of the three members java
    * declares over `Object`?
    *
    * Java's `Map.get`, `containsKey` and `remove` take an `Object`, so `Map<?, ?>` supports all
    * three and no capture is involved anywhere. Scala's `Map[K, V]` declares the same three over
    * `K`, and the ordinary rewrite therefore emits two things a wildcard receiver cannot have:
    * a key at the unnameable `K` (`Found: String / Required: map.K`, and `?1.K` where the receiver
    * is not even a stable path) and, for `get`, a `null` ascribed to the equally unnameable `V` —
    * which renders `null.asInstanceOf[?]`, a `?` in a TERM position, which is not syntax. Measured
    * on liqp at 10 and 8 errors respectively, from the same nine call sites.
    *
    * This is K10's rule met at the OTHER kind of unnameable key. There the strip was structural and
    * named no type; here so is the test — the wildcard is in the type THIS PHASE rendered, so
    * asking whether it is there is asking the phase's own record (§4.56). The three helpers take
    * the key as `Any`, which is java's own contract, so nothing is approximated.
    *
    * `put` and `getOrDefault` are deliberately absent: each needs a VALUE at the capture, and javac
    * rejects both on a `Map<?, ?>` for exactly that reason. There is no java to translate. */
  private def wildcardMapCall(name: String, recv: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (actualOf(recv)._1 match
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)

  private def wildcardMapSym(name: String): SymId = name match
    case "get"         => mapGetSym
    case "containsKey" => mapContainsKeySym
    case "remove"      => mapRemoveSym
    case _             => SymId.None

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

  private def keyType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(k, _)) => Some(k)
    case _                                   => None

  /** A key argument, with the coercion JAVA's formal required stripped when the SCALA member's
    * formal is exactly what lies beneath it.
    *
    * Java declares `Map.get`, `remove` and `containsKey` over `Object`, so a key whose static type
    * is a TYPE VARIABLE arrives here already widened — the frontend synthesises
    * `key.asInstanceOf[java.lang.Object]` off the DECLARED formal, which is right for a call to a
    * java `Map` (ENGINE-LIMITS G14). Scala's `Map[K, V]` declares the same three over `K`, so once
    * this phase has retyped the receiver that widening is the one thing standing between the
    * argument and the parameter:
    *
    * {{{ Found: Object / Required: K }}}
    *
    * ENGINE-LIMITS K5.6 in a new place — a coercion that only becomes wrong AFTER a retyping — and
    * the rule it states is why this lives here rather than in the frontend: a phase that retypes
    * must ask what it has done to the casts around the types it moved. The frontend cannot know;
    * only the phase that moved the receiver does.
    *
    * The test is STRUCTURAL and names no type (CLAUDE.md §4.56): the cast is stripped exactly when
    * what it wraps ALREADY has the type the rewritten member wants. A key that is genuinely
    * something else — java permits any `Object` — is left alone, and the boundary then fails to
    * compile naming the two types, which is the error a reader can act on (ENGINE-LIMITS M6). */
  private def keyArg(arg: Term, recv: Term): Term = (arg, keyType(recv.tpe)) match
    case (Tree.Typed(inner, _, _, _), Some(k)) if k != TypeRepr.NoType && inner.tpe == k => inner
    case _                                                                               => arg

  /** [[keyArg]]'s rule at `toArray(T[])` — the ERASURE coercion the frontend synthesised off java's
    * formal, stripped exactly where the scala helper wants what lies beneath it.
    *
    * Java declares `<T> T[] toArray(T[] a)`, whose erased formal is `Object[]`, so the frontend
    * emits `new LNode[n].asInstanceOf[Array[Object]]` (G14, the same rule that widens a map key to
    * `Object`). `JavaCollections.toArray[A]` infers `A` FROM the argument, so with the cast left on
    * it infers `Object` and hands back an `Array[Object]` where java's call — which inferred
    * `T = LNode` from the unerased argument — produced an `LNode[]`. Scala's arrays are invariant,
    * so that is a compile error at the slot rather than anything silent; but it is a compile error
    * the rewrite MADE, and the fix is to give the helper the type java's own inference saw.
    *
    * The test is STRUCTURAL and names no type (CLAUDE.md §4.56): strip when what the cast wraps
    * ALREADY has the type this call RESULTS in — that is precisely "java inferred `T` from the
    * unerased argument", because the call's recorded result type IS `T[]`. A java source that
    * really wrote `(Object[]) xs` inferred `T = Object`, so the call's result type is the CAST's
    * type and not the inner's, the guard fails, and the cast stays. */
  private def arrayArg(arg: Term, t: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) if inner.tpe != TypeRepr.NoType && inner.tpe == t.tpe => inner
    case _                                                                                => arg

  private def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type, if it is one of our scala
    * collections → its [[Kind]]. */
  private def kindAt(recv: Term)(using Program): Option[Kind] = headSym(actualOf(recv)._1).flatMap(kindOf.get)

  /** the kind of a call the receiver INHERITED — read off the RESOLVED METHOD's declaring type.
    *
    * [[kindAt]] asks what the receiver IS, and answers `None` for the one shape a library that
    * defines its own collection is made of: a class that EXTENDS a mapped JDK collection. Its type
    * is its own (`SortableMap`, and after retyping `mutable.HashMap[…] & Comparable[…]`), which
    * this phase never minted and `kindOf` therefore has no key for — so `this.get(k)`,
    * `super.putAll(m)` and `super.entrySet()` inside such a class went through untouched, and
    * `this.get(k)` then bound to scala's `Map.get` and returned an `Option` where java returned the
    * value. ENGINE-LIMITS K5 closed this family for the SHIM targets, where the parent is a
    * `balticporter.runtime` type and the shim carries java's own member names; it is still open
    * wherever the parent becomes a real scala collection, which is every `extends HashMap`.
    *
    * The question CLAUDE.md §4.56 demands is still answered from what the PHASE ITSELF did: the
    * resolved method is `java.util.Map#get`, and its OWNER is a key in this phase's own `typeMap`.
    * That is the identification [[staticRewrite]] already uses for `stream()`, applied to an
    * instance call — and it is strictly narrower than a name test, because a method the phase did
    * not retype has an owner the table does not answer for.
    *
    * SUPPRESSED for a receiver this run's scope held back. That is CLAUDE.md §1(b)'s named failure:
    * a declaration the port asked to keep in the JDK shape still RESOLVES `addAll` to
    * `java.util.List#addAll`, so the fallback alone would rewrite `b.raw.addAll(mine)` to
    * `b.raw ++= mine` against a real `java.util.List` — emitted, uncompilable code produced by the
    * scope that was supposed to protect that declaration. [[actualOf]]'s second component is
    * exactly the flag for it, and it reads `false` for every port that sets no scope. */
  private def inheritedKind(recv: Term, m: SymId)(using p: Program): Option[Kind] =
    if actualOf(recv)._2 then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  /** the type a term REALLY has — [[CollectionsTransform.scopedType]] against THIS run's
    * [[excluded]] set, with the flag that says the answer came from a declaration the scope held
    * back. `excluded.isEmpty` — the default scope, and any scope that matched nothing — always
    * answers `(t.tpe, false)`, which is the pre-scope code path by arithmetic. */
  private def actualOf(t: Term)(using Program): (TypeRepr, Boolean) =
    if excluded.isEmpty then (t.tpe, false)
    else CollectionsTransform.scopedType(t, excluded).map(_ -> true).getOrElse(t.tpe -> false)

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None

object CollectionsTransform:

  /** Pair a class body with its mapped form, POSITION BY POSITION — and refuse loudly if the two
    * lengths differ.
    *
    * `restoreExcluded` splices held-back members back by position, and its whole argument is that
    * `StandardTraversal.mapClassDef` returns the same kinds in the same order, so the two zip
    * exactly. `zip` TRUNCATES when they do not: a phase that one day inserts or drops a body member
    * would silently lose the tail of the restore — every member after the difference keeping its
    * MAPPED form whatever the scope said — with no exception, no count moving, and a port that
    * compiles. That is the same class of defect as every other silent truncation in this engine, so
    * the assumption is asserted where it is made rather than left in a doc comment. */
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

  /** The type a term REALLY has when it names a declaration a [[balticporter.tir.RuleScope]] held
    * back — `None` when it names no such declaration, in which case the node's own `tpe` is the
    * answer.
    *
    * ==Why a node's own type cannot be trusted across a scope==
    * `transformType` is POSITION-BLIND by construction: the traversal routes every type occurrence
    * through it, so a REFERENCE to an excluded field or method has its node `tpe` remapped to the
    * scala shape even though the declaration it names kept the JDK type. Every reader that asks
    * "what is this value" from the node therefore gets the answer for a declaration that did not
    * move, and the two readers that matter both get it wrong in a way nothing else can see:
    *
    *   - the TRANSFORM rewrites the call shape — `b.raw.addAll(mine)` became `b.raw ++= mine`
    *     against a `java.util.List`, i.e. emitted code that cannot compile, produced BY the scope
    *     that was supposed to protect that declaration;
    *   - the CHECK compares `Buffer` against `Buffer` and reports ZERO on exactly the seam the
    *     scope created — the one slot a scope is guaranteed to open (§1(b): every seam the scope
    *     creates is COUNTED).
    *
    * So both ask the DECLARATION instead, through this one function: two copies of a rule this
    * subtle is one copy too many, and the check's classification (`Issue.ScopedOut`) is only
    * honest if the transform drew the line in the same place. Same rule as CLAUDE.md §4.56's
    * general form — a phase may conclude something about a type only from what the phase itself
    * did to it, and what it did here is recorded in `scopedOut`.
    *
    * Only the HEAD of the result is ever read by either caller, so taking a method's RESULT type
    * for a call loses nothing an instantiation would have carried. A declaration whose `info` the
    * frontend could not record (`NoType`) answers `None` — the node is then the only evidence
    * there is. */
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

  /** java's UNIVERSAL supertype — the one formal at which every value conforms, and therefore the
    * one at which conformance proves nothing.
    *
    * This is not §4.56's forbidden name test and the difference is worth stating, because the two
    * look identical. That rule forbids concluding a type's PROVENANCE from its spelling — "starts
    * with `java.`, so the phase may delete this cast" — because a prefix is a fact about a string.
    * `java.lang.Object` is not a prefix and not a library's type: it is a fact about the JAVA
    * LANGUAGE, exactly as [[typeMap]]'s own keys and `CollectionClosureCheck.jdkFamily` are, and it
    * is asked as an EQUALITY at a slot the phase already knows is a class file's.
    *
    * Its consequence is the seam with NO COMPILE ERROR behind it: a retyped collection reaching an
    * `Object` formal conforms (`mutable.Map` is an `AnyRef`), so the port compiles and reflective
    * third-party code — a serializer, a `toString`, an `instanceof` — sees something java never
    * handed it. */
  private[balticporter] val ObjectFqn = "java.lang.Object"

  /** java fully-qualified name -> (scala fully-qualified name, collection kind).
    *
    * The phase's POLICY, in the companion so a CHECK can read it without constructing a phase:
    * `JdkSurfaceCheck` decides "did something retype this member's owner" from THIS table and never
    * from the type's name (CLAUDE.md §4.56). It is a constant — no entry depends on an instance —
    * so moving it here changes nothing about what the phase does.
    *
    * '''It must stay BELOW the `*Fqn` vals.''' Four entries name them, and an `object`'s vals
    * initialise in DECLARATION order: declared above, this table is built with four `null` targets
    * and the phase then throws a `NullPointerException` deep inside `run` — measured, 49 corpus
    * tests, and DESIGN.md §8.10's class-initialisation watch note in its smallest possible form.
    * `CollectionsHandledDerivationSpec` asserts no target is null for exactly that reason.
    */
  private[balticporter] val typeMap: Map[String, (String, Kind)] = Map(
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

  /** Can `JavaCollections.fromJava`/`toJava` express a LIVE view for this retype target?
    *
    * Five of the mapping's targets have a `scala.jdk.CollectionConverters` wrapper on the other
    * side and are therefore convertible with no copy; `JavaCollection` — what `java.util.Collection`
    * and `AbstractCollection` map to — has none, because the shim's factories build over a
    * `Buffer`, and building one from a raw `java.util.Collection` is a COPY that detaches both
    * directions. That refusal is COUNTED at the seam rather than silently taken (M6).
    *
    * A TARGET test and not a source-name test: it is the phase's own table read in the direction
    * the phase moved it (§4.56), so `java.util.ArrayList` — whose target is `ArrayBuffer`, a type
    * no converter produces — is refused for the same reason and by the same arithmetic, rather than
    * being wrapped into something narrower than the value it names. */
  private[transform] def liveWrappable(target: String): Boolean = Set(
    "scala.collection.mutable.Buffer", "scala.collection.mutable.Set", "scala.collection.mutable.Map",
    JavaIteratorFqn, JavaIterableFqn,
  ).contains(target)

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

  val StaticHelpers: List[String] =
    List("sort", "sortNatural", "reverse", "shuffle", "swap", "asList", "removeValue",
         "comparingByKey", "comparingByValue", "sortedWith", "into", "mapToDouble", "intRange",
         "toArray", "emptyList", "emptyMap", "emptySet", "singletonList", "singleton", "singletonMap",
         "unmodifiableList", "unmodifiableSet", "unmodifiableMap", "subList", "putIfAbsent",
         "toSet", "toMap", "fromJava", "toJava", "mapGet", "mapContainsKey", "mapRemove")

  // -------------------------------------------------------------------------------------------
  // WHAT THIS PHASE HANDLES, as data — the answer `JdkSurfaceCheck` needs and the arms cannot give
  // -------------------------------------------------------------------------------------------
  //
  // `staticRewrite` and `rewrite` are `match` arms, so nothing can ask "what does this phase
  // cover?" — which is precisely why the engine's JDK coverage was invisible until a compile error
  // named a hole. These two tables are that question answered, and they are DECLARED rather than
  // derived at runtime because a regex over one's own source is not something production code
  // should do. Their agreement with the arms is asserted by `CollectionsHandledDerivationSpec`,
  // which scans this file's SOURCE TEXT in both directions — a table beside the code it describes
  // is only worth having while the two agree, and neither a stale entry nor a missing one moves any
  // other count: a missing entry makes a handled member read as the port's JDK wall, and a stale
  // one makes a real hole read as covered.

  /** every `owner#name` a [[staticRewrite]] arm matches, INCLUDING the two collector keys the
    * `collect` arms read out of a guard — a `Collectors.toList()` call is subsumed by the collapse
    * exactly as `Collections.sort` is subsumed by its factory, and a table that omitted them would
    * report the port's own translation as its wall. */
  val handledStatics: Set[String] = Set(
    "java.util.Arrays#asList",
    "java.util.Collection#stream",
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
    "java.util.stream.Stream#collect",
    "java.util.stream.Stream#filter",
    "java.util.stream.Stream#map",
    "java.util.stream.Stream#mapToDouble",
    "java.util.stream.Stream#sorted",
  )

  /** collection KIND → the instance member names [[rewrite]] handles for it, with
    * `JdkSurfaceCheck.AnyKind` for an arm whose kind pattern is `_`.
    *
    * The split across kinds is READ BY EYE and deliberately not asserted: the arms are keyed on
    * `(name, args, kind)` and one `add` arm serves every Seq-shaped java type, so only the UNION is
    * mechanically derivable. Assigning a name to more kinds than its arm covers can make the check
    * kinder (a `mapped` row that could have been a finding) and can never make it miss a hole in a
    * kind that genuinely has none. */
  val handledInstance: Map[String, Set[String]] = Map(
    balticporter.tir.JdkSurfaceCheck.AnyKind -> Set(
      // arms whose kind pattern is `_` …
      "forEach", "iterator", "getOrDefault", "add", "addAll", "putAll",
      // … and `parenless`, which is an arm of its own (`case (n, Nil, _) if parenless(n)`)
      "size", "isEmpty", "keySet", "values", "nonEmpty", "hasNext", "next",
    ),
    Kind.Seq.toString   -> Set("get", "set", "remove", "addLast", "offer", "offerLast",
                               "addFirst", "offerFirst", "poll", "pollFirst", "peek", "peekFirst", "element",
                               "toArray", "subList"),
    Kind.Map.toString   -> Set("get", "put", "remove", "containsKey", "entrySet", "values", "putIfAbsent"),
    Kind.Set.toString   -> Set("remove", "toArray"),
    Kind.Entry.toString -> Set("getKey", "getValue"),
  )

  /** This phase's record, in the shape [[balticporter.tir.JdkSurfaceCheck]] reads.
    *
    * `ran` is the caller's to supply and is not a property of the phase: the same tables answer two
    * different questions depending on whether the phase is in the pipeline. With it absent an
    * unhandled member on a mapped type is an OFFER (`mappable`, report-only — noise4j's deliberate
    * position); with it present the same member is a hole the phase MADE, and is a finding. */
  def jdkMapping(ran: Boolean): balticporter.tir.JdkSurfaceCheck.Mapping =
    balticporter.tir.JdkSurfaceCheck.Mapping(
      phase        = "java-collections->scala",
      ran          = ran,
      types        = typeMap.view.mapValues((target, kind) => (target, kind.toString)).toMap,
      statics      = handledStatics,
      instance     = handledInstance,
      // the SHIMS' own members, from the artifact's map — pinned to the published runtime sources by
      // `RuntimeMembersDerivationSpec`, so `java.util.Iterator#remove` is `shimmed` because the shim
      // really declares it and not because someone remembered to say so here.
      shimMembers  = RuntimeArtifact.concreteMembers.view.mapValues(_.map(_._1)).toMap,
      iterableShim = Some(JavaIterableFqn),
      // `new` on a retyped type is rewritten by three paths — the `tpt` retyping itself,
      // `copyConstructor` and `capacityConstructor` — none of which is a member table entry,
      // because a constructor is not a member call. ENGINE-LIMITS K11 is the arity correspondence
      // between the two constructors failing, and it is this phase's business, not the check's.
      constructors = true,
    )

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
