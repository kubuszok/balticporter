package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, RequiresRuntime, RuntimeArtifact, SurfacePolicy}
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
    /** REIFIED CARRIERS — external generic types whose type ARGUMENTS a third party reads back out
      * of the class file's generic signature at run time (`ENGINE-LIMITS.md` K20).
      *
      * The retyping is right at every static slot and is a claim about a SLOT. A carrier's argument
      * is not one: it survives erasure into the generic signature, and jackson's
      * `TypeReference<Map<String,Object>>`, Gson's `TypeToken<T>` and Guice's `Key<T>` are read back
      * and CONSTRUCTED FROM. Moved to `mutable.Map`, the port stops describing a slot and starts
      * telling the framework to instantiate a trait — `Cannot construct instance of
      * scala.collection.mutable.Map`, at run time, with 0 compile errors and every check count flat.
      *
      * So the argument stays in java's namespace ([[preservesTypeArgsOf]]) and the value is BRIDGED
      * where it is used — which needs no new machinery, because a call whose result is now java's
      * against a node that claims a mapping target is exactly the external-producer seam
      * ([[externalProducer]]): it wraps into a live view where one exists, and counts the slot where
      * none does. Do NOT reach for the two nearby answers instead — retyping-and-wrapping at the
      * PRODUCER is K18's own 44-of-160 dead end (`asScala` is one level), and holding the whole
      * declaration out with [[scope]] is K16 (27 → 47 errors). The argument has to stay java's while
      * the surrounding declarations keep the mapping, which is what a per-ARGUMENT carrier list
      * expresses and neither of those does.
      *
      * ==WHICH types are carriers is per-library, and the one universal is not a parameter==
      * A carrier is a fact about a library's DEPENDENCIES, so the list is §1(b) policy and empty is
      * the default — every arm below is a no-op by arithmetic where a port declares none.
      * [[CollectionsTransform.UniversalCarriers]] is added to whatever the port declares, and it is
      * not policy: `java.lang.Class<T>` is reified by java ITSELF, in every codebase there will ever
      * be, which is §1(a) and belongs in this file exactly as `typeMap` does. */
    val reifiedCarriers: Set[String] = Set.empty,
    /** REFLECTIVE SINKS — external types that read the RUNTIME REPRESENTATION of a value handed to
      * them at an opaque slot (`ENGINE-LIMITS.md` K21 face 1).
      *
      * [[reifiedCarriers]] is the same third party reading the class file's TYPE ARGUMENTS; this is
      * the same third party reading the OBJECT. A serialiser, a bean mapper or an injector is
      * declared `f(java.lang.Object)`, so the port's retyped collection CONFORMS — there is no slot
      * whose sides disagree, no compile error and no seam any count can see — and what the callee
      * then does with it is decided by the value's class, which the retyping moved:
      * `writeValueAsString(aMap)` emits `{"scala$collection$mutable$HashMap$$table":[…]}` where java
      * emitted the map's entries.
      *
      * The bridge is [[CollectionsTransform.ReifiedFqn]]`.toJavaValue`, and it is a RUN-TIME
      * question because there is no static evidence at the call: the argument's own type is
      * `java.lang.Object` too — the port's data model holds both representations at every such slot
      * (K18) — so the phase cannot know, and the object can. It is identity for everything this
      * engine did not put there, deep-by-view for everything it did.
      *
      * ==WHICH types are sinks is per-library, and there is no universal one to add==
      * Unlike [[reifiedCarriers]], whose `java.lang.Class` java itself guarantees, java guarantees
      * NO reflective sink: reading a value's representation is what a DEPENDENCY does, so the list
      * is entirely §1(b) policy and empty is the default and the no-op. The match is on the callee's
      * OWN owner, so a sink reached through a sibling facade (a writer, a builder) is a second entry
      * rather than an inference — a never-fired entry is reported by the ordinary policy lane, and a
      * MISSING one is invisible, which is what the `OpaqueEgress` boundary count exists to show.
      *
      * The parameter is deliberately NOT part of [[surfaceFingerprint]]: it changes arguments inside
      * bodies and no signature, so a base and a dependent that declare different sinks still emit
      * surfaces that compile together — which is the question that fingerprint answers. */
    val reflectiveSinks: Set[String] = Set.empty,
    /** ADDITIONAL COLLECTION FAMILIES — java FQN -> (scala FQN, Kind), retyped and API-mapped
      * alongside the JDK entries in [[CollectionsTransform.typeMap]].
      *
      * ==Why this is a SECOND table and not part of the companion's `typeMap`==
      * `typeMap` is a §1(a) constant — the JDK collections, true of every codebase. THIS table is
      * §1(b) policy: WHICH library types are also collections, and what their scala targets are, is
      * knowledge about that library. A libGDX `Array` is `mutable.ArrayBuffer`; a Guava
      * `ImmutableList` might be `List`. The engine owns the MECHANISM (retype, rewrite kind-aware,
      * coerce at seams); the manifest owns WHICH types enter it.
      *
      * Entries are merged INTO [[typeMap]] at construction time, so every arm that reads `typeMap`
      * — `remap`, `kindOf`, `mappedTypes`, `targetOf`, every `Kind`-keyed rewrite — sees these
      * entries by construction with no new guard anywhere. A key that collides with a JDK entry or
      * with a `retarget` key is REFUSED at construction, the same rule `retarget` applies.
      *
      * Empty is the default and the no-op: no entry, no retype, no rewrite, no fingerprint segment,
      * and the code path is the JDK-only path by the same arithmetic (§1(b)'s rule). */
    val families: Map[String, (String, CollectionsTransform.Kind)] = Map.empty,
    /** PER-ENTRY SCOPES for [[families]] — java source FQN -> `RuleScope`.
      *
      * Follows the `TypeRedirectTransform.scopes` pattern for `ENGINE-LIMITS.md` D12's reason: a
      * base states a whole-program family, a dependent states one scoped to its own declarations,
      * and `surfaceFold` merges the two into ONE phase, so a single scope on the phase cannot serve
      * both. Keyed by the family's SOURCE FQN; a key with no family entry is ignored. Default
      * `Everywhere(Set.empty)` — the pre-scope code path. */
    val familyScopes: Map[String, RuleScope] = Map.empty,
) extends Phase, Rewrite, RequiresRuntime, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  def name = "java-collections->scala"

  /** THE THREE LANES that count what this retyping opened and could not close (`Rewrite`).
    *
    * Three and not one, because they are three different residues and a reader acts on each
    * differently: [[CollectionClosureCheck]] is a TYPE the mapping does not reach, [[CollectionBoundaryCheck]]
    * is a SLOT whose two sides ended up on opposite sides of a line this phase drew, and
    * [[RetargetBoundaryCheck]] is the direction a subtyping argument does not license — a value the
    * JDK PRODUCES at a type this phase retargeted, which the boundary check cannot see because the
    * position-blind retyping moved the node type on both sides of that slot. Named as symbols
    * rather than as strings so a renamed lane is a compile error and not a silently unwired claim. */
  def accountedBy: Set[String] =
    Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name, RetargetBoundaryCheck.Name)

  /** What the RUN resolved each declared scope entry to, before the pipeline started (§8.1). This
    * phase is the one whose own matcher already reports [[balticporter.tir.NotBound.ExternalOnly]]
    * (see `applyScope`), so the `policy-binding` measurement over the corpus is largely a
    * measurement of whether the binder reproduces the answer this phase worked out by hand. */
  private var boundScope: Map[String, Binding[Unit]] = Map.empty

  /** …and each retarget SOURCE. Bound as [[Ownership.Either]] on purpose: a retarget's subject is a
    * type this program REFERENCES and never declares — a JDK interface — which is the one shape
    * `Owned` would report as never-matched while the rewrite worked. */
  private var boundRetarget: Map[String, Binding[SymId]] = Map.empty

  /** …and each declared REIFIED CARRIER. [[Ownership.Either]] for the same reason a retarget takes
    * it and a sharper one: a carrier is by construction a type the program only REFERENCES — a
    * third party's super-type token — so `Owned` would report every entry as never-matched while
    * the preservation worked. A carrier the program never names binds to nothing and is reported by
    * the ordinary never-fired lane, which is the answer a port wants: a jackson entry in a port that
    * does not use jackson is a line to delete. */
  private var boundCarriers: Map[String, Binding[SymId]] = Map.empty

  /** …and each declared REFLECTIVE SINK, for the reason a carrier takes [[Ownership.Either]] and
    * with no exception: a sink is by construction a type the program only REFERENCES. */
  private var boundSinks: Map[String, Binding[SymId]] = Map.empty

  /** …and each declared FAMILY SOURCE. [[Ownership.Either]] for the same reason a retarget takes
    * it: a family source is a type this program REFERENCES but usually does not declare — a
    * library's own collection class, resolved from the resolution roots. */
  private var boundFamilies: Map[String, Binding[SymId]] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val setting = s"CollectionsTransform(scope) ${scope.productPrefix} entry"
    boundScope = scope.entries.toList.sorted.map(e => e -> binder.bindScope(name, setting, e)).toMap
    boundRetarget = retarget.keys.toList.sorted
      .map(k => k -> binder.bindType(name, RetargetSetting, k, Ownership.Either)).toMap
    boundCarriers = reifiedCarriers.toList.sorted
      .map(k => k -> binder.bindType(name, CarrierSetting, k, Ownership.Either)).toMap
    boundSinks = reflectiveSinks.toList.sorted
      .map(k => k -> binder.bindType(name, SinkSetting, k, Ownership.Either)).toMap
    boundFamilies = families.keys.toList.sorted
      .map(k => k -> binder.bindType(name, FamilySetting, k, Ownership.Either)).toMap

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
    val parts = List(
      // …and the MAPPING TABLE itself, which is not a constructor parameter and is surface all the
      // same. Everything else here differs between two INSTANCES; this differs between two ENGINE
      // BUILDS, and §1.5's question — "can these two modules' emitted signatures compile together?"
      // — is asked of a base that was ported months ago against a dependent ported today. A base
      // whose `Vector` fields are `java.util.Vector` and a dependent whose are `ArrayBuffer` emit
      // exactly the incompatible pair `SurfacePolicy` exists to catch, and NOTHING else in the port
      // map could see it: `engine=` is a released version string that does not move between
      // commits, and the source digest is about the base's JAVA, which did not change at all.
      //
      // A DIGEST rather than the table, because the fingerprint is compared and published, never
      // read: a hundred `k->v` pairs in every port map's header is a diff nobody reads. It is
      // rendered unconditionally, so it is also the answer to "did this change reach the
      // fingerprint" — a mapping edit that left every published map identical would mean it had not.
      Some("mapping=" + CollectionsTransform.mappingDigest),
      scala.Option.when(retarget.nonEmpty)(
        "retarget=" + retarget.toList.sorted.map((k, v) => s"$k->$v").mkString(",")),
      // A CARRIER is the same fact one type argument in, and it is surface for the plainest reason
      // there is: `TypeReference<Map<String,Object>>` is the emitted type of a `public static final`
      // field. A base that preserves it and a dependent that does not emit two signatures that each
      // compile alone and cannot compile together — SurfacePolicy's case exactly (§1.5).
      scala.Option.when(reifiedCarriers.nonEmpty)(
        "carriers=" + reifiedCarriers.toList.sorted.mkString(",")),
      // A FAMILY is the same surface fact as the JDK mapping, one library further out: a base whose
      // `com.badlogic.gdx.utils.Array` fields became `ArrayBuffer` and a dependent whose did not
      // emit signatures that cannot meet. Segment omitted when empty, so a port that declares no
      // families has the fingerprint it always had and no baseline moves (§1(b)'s fingerprint rule).
      scala.Option.when(families.nonEmpty)(
        "families=" + familiesDigest),
    ).flatten
    // `retarget`, `carriers` and `families` are rendered only when non-empty, so a port that
    // declares none adds nothing; `mapping` is always there, which is why there is no longer an
    // empty case.
    s"${scope.fingerprint};${parts.mkString(";")}"

  // ---- MergeablePolicy: HOW this table composes with a nearer manifest's instance ----

  /** Every shared-surface SUBJECT this instance's policy is keyed on.
    *
    * The JDK entries are NOT subjects: they are §1(a) universal, present in every instance, and
    * screening them would make every family key a `SurfaceIntrusion` against the base's `governs`.
    * Retargets and families ARE subjects — each is keyed on a name that may be in the shared
    * surface. Carriers and sinks are not surface (they change bodies, not signatures). */
  def subjects: Set[String] =
    (retarget.keySet ++ families.keySet).map(MergeablePolicy.subjectOf)

  /** Merge `later` into this. A dependent ADDS families and retargets; same source with a different
    * target refuses with the phase's sentence. Scopes on the SAME family source must agree. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: CollectionsTransform =>
      // --- retarget clashes ---
      val retargetClash = (retarget.keySet & o.retarget.keySet).filter(k => retarget(k) != o.retarget(k))
      // --- family clashes: same source, different (target, kind) ---
      val familyClash = (families.keySet & o.families.keySet).filter(k => families(k) != o.families(k))
      // --- family/retarget cross-clash: a key in both tables ---
      val crossClash = (families.keySet & o.retarget.keySet) ++ (retarget.keySet & o.families.keySet)
      // --- scope clashes on families both sides declare ---
      val scopeClash = (families.keySet & o.families.keySet).toList.sorted
        .filter(k => familyScopeOf(k) != o.familyScopeOf(k))
      // --- scope clashes on retargets both sides declare ---
      val retargetScopeClash = (retarget.keySet & o.retarget.keySet).toList.sorted
        .filter(k =>
          scope.entries.intersect(Set(retarget(k))).nonEmpty !=
          o.scope.entries.intersect(Set(o.retarget(k))).nonEmpty) // rough — retargets do not have per-entry scopes
      // --- carrier/sink disagreements are NOT surface and therefore NOT a refusal ---
      if retargetClash.nonEmpty || familyClash.nonEmpty || crossClash.nonEmpty ||
          scopeClash.nonEmpty then
        Left(
          (retargetClash.toList.sorted.map(k =>
             s"""both modules retarget "$k", to "${retarget(k)}" and "${o.retarget(k)}"""") ++
           familyClash.toList.sorted.map(k =>
             s"""both modules declare a family for "$k", """ +
               s"""to (${families(k)._1}, ${families(k)._2}) and (${o.families(k)._1}, ${o.families(k)._2})""") ++
           crossClash.toList.sorted.map(k =>
             s""""$k" appears in families on one side and retarget on the other — two answers for one type""") ++
           scopeClash.map(k =>
             s"""both modules scope the family "$k" and disagree — """ +
               s""""${familyScopeOf(k).fingerprint}" and "${o.familyScopeOf(k).fingerprint}"; a scope """ +
               "decides which declarations carry the family type in their signatures"))
            .mkString("; ") +
            " — two answers for one key is a rewrite whose outcome depends on which manifest was read")
      else
        val mergedRetarget = retarget ++ o.retarget
        val mergedFamilies = families ++ o.families
        val mergedFamilyScopes = familyScopes ++ o.familyScopes
        // carriers and sinks: union without a clash (they are not surface)
        val mergedCarriers = reifiedCarriers ++ o.reifiedCarriers
        val mergedSinks = reflectiveSinks ++ o.reflectiveSinks
        val addedRetargetSubjects = (o.retarget.keySet -- retarget.keySet).map(MergeablePolicy.subjectOf)
        val addedFamilySubjects = (o.families.keySet -- families.keySet).map(MergeablePolicy.subjectOf)
        Right(MergeablePolicy.Merged(
          new CollectionsTransform(
            scope          = scope, // the base's scope — inherited
            retarget       = mergedRetarget,
            reifiedCarriers = mergedCarriers,
            reflectiveSinks = mergedSinks,
            families       = mergedFamilies,
            familyScopes   = mergedFamilyScopes),
          addedRetargetSubjects ++ addedFamilySubjects))
    case other =>
      Left(s"`${other.name}` is not a `CollectionsTransform`, so there is no table to compose")

  /** this phase retypes onto `balticporter.runtime` — declared once, so the run derives the port's
    * dependency, its vendored sources and the emitter's external-parent table from it. */
  def runtimeTypes: Set[String] = CollectionsTransform.runtimeTypes

  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  // ---- COLLISION CHECK: a family key that also appears in the JDK table or in `retarget` is
  // refused at construction, the same rule `retarget` applies — two answers for one type is a
  // rewrite whose outcome depends on which table was read ----
  locally {
    val jdkClash = families.keySet & CollectionsTransform.typeMap.keySet
    require(jdkClash.isEmpty,
      s"CollectionsTransform: families key(s) ${jdkClash.mkString(", ")} also appear in the JDK " +
        "typeMap — two answers for one type is not a thing a policy author can reason about")
    val retargetClash = families.keySet & retarget.keySet
    require(retargetClash.isEmpty,
      s"CollectionsTransform: families key(s) ${retargetClash.mkString(", ")} also appear in " +
        "retarget — two answers for one type is not a thing a policy author can reason about")
  }

  /** java fully-qualified name → (scala fully-qualified name, collection kind).
    *
    * The JDK table lives in the COMPANION ([[CollectionsTransform.typeMap]]) — it is a §1(a)
    * constant and `JdkSurfaceCheck` has to be able to read it without constructing a phase (§4.56).
    * FAMILIES are merged in here, so every arm that reads `typeMap` — `remap`, `kindOf`,
    * `mappedTypes`, `targetOf`, every `Kind`-keyed rewrite — sees both JDK and family entries by
    * construction with no new guard anywhere. */
  private val typeMap: Map[String, (String, Kind)] = CollectionsTransform.typeMap ++ families

  /** the RuleScope a FAMILY ENTRY declares — `Everywhere(Set.empty)` (the pre-scope code path)
    * when the source FQN has no explicit scope. JDK entries are not scoped through this map;
    * they use the phase-level `scope` as they always have. */
  def familyScopeOf(from: String): RuleScope = familyScopes.getOrElse(from, RuleScope.everywhere)

  /** digest of the `families` table — sorted by source FQN, carrying the target and kind. The
    * scope is part of the fingerprint too (a scope difference is a surface difference), so it joins
    * the sorted string. Used only when `families.nonEmpty`. */
  private def familiesDigest: String =
    balticporter.tir.TirPrinter.sha256(
      families.toList.map((k, v) => s"$k->${v._1}:${v._2};scope=${familyScopeOf(k).fingerprint}")
        .sorted.mkString(",")).take(16)

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
    CollectionBoundaryCheck.check(program, units, mappedTypes, retypedTargets, scopedOut,
                                  classFileOverrides) ++
      // …plus the EXTERNAL seams, which the check cannot re-derive: by the time it runs, the
      // position-blind retyping has moved the node's type on BOTH sides of every one of them, so a
      // walk over the post-phase tree reports zero. They are recorded during the traversal, while
      // the external signature is still readable, and filtered to the units this run EMITS for
      // ENGINE-LIMITS D2's reason — a dependent's program contains its base's units, and a seam
      // inside one of those is the base's finding.
      externalSeams.toList.filter(f => emittedPaths(units).contains(f.origin.javaPath)) ++
      // …and the OPAQUE EGRESS review list (K21 face 1), built the same way and for the same
      // reason: the formal is `java.lang.Object`, so after the retyping there is nothing on either
      // side of the slot for a walk to disagree about. One row per external CALLEE.
      //
      // THE DEDUP AND THE D2 FILTER ARE APPLIED IN THAT ORDER, and getting it the other way round
      // is silent. "One row per callee" is right; the SITE chosen for that row is a reporting
      // detail — and a single global minimum makes it a fact about the whole program, which the
      // filter below then reads as if it were the population. A dependent whose base reaches the
      // same callee at a smaller (path, line) loses its row entirely: it has the seam, has no
      // finding, and nothing anywhere says so. So the recording is per (callee, java file) and the
      // MINIMUM is taken among the paths THIS module emits — one row per callee still, at a site
      // its reader can open.
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

  /** an EXTERNAL callee as a reader must be able to act on it: `<owner FQN>#<member>`.
    *
    * An external member's own `fullName` is interned as `@<id>#name(params)` — the owner is a class
    * file the frontend never gave a name to at that position — and the owner FQN is exactly the
    * string a port would put in `reflectiveSinks`, so a row without it is a row nobody can use. */
  private def calleeLabel(m: SymId)(using p: Program): String =
    memberKeyOf(m).getOrElse("?")

  /** `<owner FQN>#<member>` for a callee, in the one grammar this repository writes member identity
    * in. Two readers: [[handledStatic]] asks the phase's own table with it, and [[calleeLabel]]
    * prints it. */
  private def memberKeyOf(m: SymId)(using p: Program): Option[String] =
    p.symbolOf(m).flatMap(c => p.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))

  /** the java files the units this run emits came from — the D2 filter, by SOURCE PATH, because a
    * recorded seam carries its `Origin` and not the unit it sat in. */
  private def emittedPaths(units: List[Tree.ClassDef]): Set[String] =
    units.map(_.origin.javaPath).toSet

  /** [[CollectionInternalCheck]] over this phase's own mapping — the IN-PROGRAM half of the same
    * residue [[boundary]] counts, and the half that lane reads as zero by construction (both sides
    * of every one of its slots are this phase's own output). Run on the program AFTER the phase,
    * for [[boundary]]'s reason. */
  def internal(program: Program): List[CollectionInternalCheck.Finding] =
    internal(program, program.units)

  /** …held to the units the run EMITS (`ENGINE-LIMITS.md` D2), exactly as its three siblings are. */
  def internal(program: Program, units: List[Tree.ClassDef]): List[CollectionInternalCheck.Finding] =
    CollectionInternalCheck.check(program, units, mappedTypes, targetOf,
                                  CollectionsTransform.standaloneTargets)

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
  /** every class this program declares, by symbol — the one thing [[classTparams]] needs and the
    * only place the IR keeps a class's own type parameters. */
  private var classDefsBySym: Map[SymId, Tree.ClassDef] = Map.empty
  private var kindOf: Map[SymId, Kind]    = Map.empty // scala collection symbol → kind
  /** a class the PROGRAM declares → what this phase's own MINTED PARENTS put beside its members.
    *
    * Filled in [[run]] from the ORIGINAL units, because that is the only place the java parent is
    * still written down; read by [[pinnedByObject]]. Empty whenever the mapping covers no parent of
    * any declared class, which makes that pin a no-op by arithmetic.
    *
    * `kinds` is a SET and not one kind, because java interfaces are small and orthogonal and a
    * collection class routinely implements several — `OrderedMap implements Map<K,V>,
    * Iterable<Map.Entry<K,V>>` is `CLAUDE.md` §4.5's own sentence about the library this was written
    * from. Read as one kind the LAST parent silently won and the pin declined for the very class it
    * was written for (measured: 0 of 10 sites).
    *
    * `probes` is each mapped parent's FIRST type argument — the key of a `Map`, the element of a
    * `Set`/`Buffer` — as the `extends` clause WRITES it, which is where the one refusal is read
    * from; `tparams` is the class's own parameters, so a probe that is one of them can be resolved
    * against a receiver's instantiation.
    *
    * `shims` is the STANDALONE targets among the same parents. Those contribute no clashing member
    * (§4.5), so the pin ignores them — but they are exactly what says a value ALREADY CONFORMS at a
    * shim-typed slot, which is what [[coerce]] must not wrap.
    *
    * `targets` is the SCALA FQN each mapped parent became — what the emitted `extends` clause
    * actually says. `kinds` cannot answer that (one kind covers `mutable.Map`, `HashMap`, `TreeMap`
    * and `TrieMap`) and no reader of a refusal can be told to look at a `Kind`, so it is carried
    * rather than reverse-derived: recomputing it from `typeMap` by matching kinds would name every
    * target of that kind and not the one this class extends.
    *
    * `declared` is this class's OWN mapped clauses — the kind plus the java type ARGUMENTS the
    * clause writes — and it is deliberately not transitive where `kinds` is. The surface synthesis
    * (K28.1) reads it for two different facts at once: WHERE the obligation lands (a subclass
    * inherits its base's minted parent and must not write a second copy of every bridge) and WHAT
    * the key, value or element type is. An inherited clause answers neither — the ancestor's
    * arguments arrive unsubstituted, which is the same approximation `probes` states — so a class
    * with an empty `declared` is one the synthesis does not act on at all. */
  private final case class MintedParents(kinds: Set[Kind], probes: List[TypeRepr],
                                         tparams: List[SymId], shims: Set[String],
                                         targets: Set[String] = Set.empty,
                                         subsumed: Map[String, String] = Map.empty,
                                         declared: List[(Kind, List[TypeRepr])] = Nil)
  private var parentClash: Map[SymId, MintedParents] = Map.empty
  /** every `super.<JDK default>` this run stood on `this` instead — (enclosing class, callee, member
    * name), drained into `decisions.tsv` by [[recordSuperDefaults]]. Collected AT the rewrite rather
    * than re-derived afterwards, so the provenance and the licence are one answer and cannot drift. */
  private val superDefaults = collection.mutable.ListBuffer.empty[(SymId, SymId, String)]
  private var opPlusEq, opMinusEq, opPlusPlusEq: SymId = SymId.None
  private var updateSym, insertSym, getOrElseSym, containsSym: SymId = SymId.None
  /** scala `mutable.Map.put`/`remove` — they RETURN the previous value, which java's do too and
    * `update`/`-=` silently discard. */
  private var putSym, removeSym: SymId = SymId.None
  /** java Deque members. `poll`/`peek` return NULL on empty where scala's `head`/`remove(0)`
    * throw, so both go through an `Option` and `orNull` — the difference is invisible in a
    * compile and shows up as a MatchError-shaped failure at runtime (CLAUDE.md §4.4). */
  private var removeHeadOptionSym, headOptionSym, orNullSym, prependSym: SymId = SymId.None
  /** `java.util.Stack.empty()` — a RENAME rather than a paren strip: scala's predicate is
    * `isEmpty`, and `empty` on a `Buffer` is the companion's factory, so leaving the name alone
    * emits something that means "an empty buffer" where java asked "is this one empty". */
  private var isEmptySym: SymId = SymId.None
  /** `scala.Option`'s side of the [[Kind.Opt]] arms: `get` for `getAsInt`/`orElseThrow`,
    * `isDefined` for `isPresent`, and `Some`/`None` for the two static factories. */
  private var getSym, isDefinedSym, someSym, noneSym: SymId = SymId.None
  /** `JavaEnumMap.ofType`, the class-token constructor's target ([[tokenConstructor]]), and
    * `JavaEnumSet`'s six statics, which are the ONLY way java reaches an `EnumSet` — it has no
    * public constructor. Minted rather than resolved: nothing in a java program declares them. */
  private var enumMapOfTypeSym: SymId = SymId.None
  private var enumSetSyms: Map[String, SymId] = Map.empty
  /** this run's symbol for a scala/shim FQN, or `SymId.None` where the program never names it. */
  private var byScalaSyms: Map[String, SymId] = Map.empty
  private def byScalaSym(fqn: String): SymId = byScalaSyms.getOrElse(fqn, SymId.None)
  private def enumSetSym(n: String): SymId   = enumSetSyms.getOrElse(n, SymId.None)
  /** java 8 `Collection.forEach(Consumer)` — scala's is `foreach`, differing only in case, which
    * makes the failure read like a typo rather than a missing mapping. */
  private var foreachSym: SymId = SymId.None
  private var key1Sym, value2Sym, selfParamSym: SymId = SymId.None
  /** the BOUND method reference's two mints — the receiver's one-shot binding, and the lowered
    * lambda's argument parameters by INDEX. See [[lowerMethodRef]]'s `Right` arm.
    *
    * Four parameters and not a growing list: the members [[rewrite]] answers for are JDK COLLECTION
    * members, and the widest of them takes two (`put(K, V)`, `add(int, E)`). A wider arity therefore
    * cannot reach a rewrite at all, so the ceiling is unreachable by construction and declining
    * above it leaves the reference exactly as it was. Fixed names rather than a per-site counter,
    * which is `ENGINE-LIMITS.md` M10's rule: an emitted name keyed on anything wider than the
    * declaration that holds it turns `members.tsv` into churn.
    *
    * ==and ONE symbol per position survives NESTING, which [[selfParamSym]] does not have to argue==
    * That one is safe because two unbound lowerings can never nest. These CAN — a bound reference's
    * qualifier is an arbitrary expression and may hold another — and sharing the symbols is still
    * exact, because of WHERE each binding sits. The inner lowering lands inside the OUTER's
    * initialiser (`val recv$ = <… { val recv$ = y; … } …>`), where scala's own scoping makes it a
    * fresh binding: a `val` is not in scope in its own right-hand side. And nothing nests in the
    * other direction — the lambda's body is `recv$.m(a0$, …)`, whose arguments are bare `Ident`s, so
    * no second lowering can appear under an `a0$` that is already bound. */
  private var recvBindSym: SymId = SymId.None
  private var argParamSyms: Vector[SymId] = Vector.empty
  /** the scala side of a BRIDGED member (`ENGINE-LIMITS.md` K28.1) — the types its signature is
    * written in, and the two `asScala` views its body reaches java's answer through.
    *
    * Resolved-or-minted exactly as `unsupportedOpSym` is, and for the same reason: two symbols for
    * one FQN print the same text and compare unequal. `iteratorMemberSym` is scala's own
    * parameterless `iterator` on a collection, which is how a `Map` with no java `iterator()` of its
    * own reaches java's `entrySet().iterator()` idiom. */
  private var optionSym, scalaIteratorSym, scalaIterableSym, iterableOnceSym: SymId = SymId.None
  private var tuple2Sym, boolSym, intSym, unitSym: SymId = SymId.None
  private var asScalaIteratorSym, asScalaIterableSym, iteratorMemberSym: SymId = SymId.None
  private var unitTpe: TypeRepr = TypeRepr.NoType

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
  /** scala's own `exists`/`forall` — java's `anyMatch`/`allMatch`, which mean exactly these. */
  private var existsSym, forallSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private var iteratorFromSym, javaIteratorSym: SymId = SymId.None
  /** `JavaListIterator` and its write-through cursor `JavaListIterator.over` — the `listIterator`
    * rewrite's target (`ENGINE-LIMITS.md` K23). `SymId.None` unless the program names
    * `java.util.ListIterator`, so the arm declines by arithmetic everywhere else. */
  private var javaListIteratorSym, listIteratorOverSym: SymId = SymId.None
  /** `JavaCollections.{spliterator, orderedSpliterator, distinctSpliterator}` — java's THREE own
    * defaults for `spliterator()`, one per owner it re-declares the member at
    * (`ENGINE-LIMITS.md` K23). Three symbols and not one, because the emitted call has to NAME which
    * java declaration it reproduces rather than carry a characteristics constant. */
  private var orderedSpliteratorSym, distinctSpliteratorSym: SymId = SymId.None
  /** the mapping targets `JavaCollections.fromJava` can actually PRODUCE — see
    * [[CollectionsTransform.liveWrappable]], read as symbols so [[externalProducer]] asks a
    * membership question about what this run minted rather than a question about a name. EMPTY when
    * the program names none of them, which makes the wrap arm decline by arithmetic. */
  private var liveWrappableSyms: Set[SymId] = Set.empty

  /** each mapping target this run named → the `JavaCollections.Reified` member that answers java's
    * `instanceof` / performs java's cast at it. Keyed on `byScala`, so a target the program never
    * names is simply absent and the reified arms decline by arithmetic — the same shape
    * [[liveWrappableSyms]] takes, and for the same reason (§4.56: the phase's own record). */
  private var reifiedIsSyms, reifiedAsSyms: Map[SymId, SymId] = Map.empty

  /** every symbol THIS PROGRAM names that is an unmapped SUPERTYPE of a type this phase retypes —
    * see [[unmappedReified]]. EMPTY where the program names none of them, which makes the refusal
    * decline by arithmetic exactly as the two maps above do. */
  private var unmappedSupertypeSyms: Set[SymId] = Set.empty

  /** did a REIFIED occurrence get translated inside the declaration currently being closed?
    *
    * The traversal is BOTTOM-UP, so a term hook cannot name its enclosing declaration and the
    * declaration hook runs after its body — the same shape `TestFrameworkTransform.citeIfPromoted`
    * uses. `JS-G48` is a `Cited` row, and a citation is per DECLARATION (§5.1), so the flag is set
    * at the rewrite and drained at the nearest enclosing `DefDef`/`ValDef`. */
  private var reifiedHere: Boolean = false

  // ---- the RuleScope's own record, for THIS run (see `applyScope`) ----

  /** `JavaCollections.fromJava` / `toJava` — the EXTERNAL seam's two directions. */
  private var fromJavaSym, toJavaSym, toStreamSym: SymId = SymId.None

  /** java's three `Object`-keyed map members, and the two `Object`-keyed collection ones, for a
    * receiver or an ARGUMENT at which the element type cannot be named — see [[objectProbe]]. */
  private var mapGetSym, mapContainsKeySym, mapRemoveSym: SymId = SymId.None
  private var setContainsSym, setRemoveSym: SymId               = SymId.None

  /** the java symbols this run's mapping sends to a target that CANNOT BE A PARENT — see
    * [[restoreUninheritableParents]]. EMPTY unless the program actually names one, which makes the
    * pass a no-op by arithmetic on every port that does not. */
  private var uninheritableSyms: Set[SymId] = Set.empty

  /** …and the classes among them whose value at the TARGET's own slot really is a detached one,
    * with the target FQN it may be projected to — see [[detachedEntriesIn]]. EMPTY unless the
    * program declares such a class AND the class already refuses the write, so the projection
    * declines by arithmetic on every port that does neither. */
  private var detachedEntries: Map[SymId, String] = Map.empty

  /** `JavaCollections.entryToPair` — the projection [[detachedEntries]] licenses. */
  private var entryToPairSym: SymId = SymId.None

  /** `java.lang.UnsupportedOperationException`, as this run's own type — see
    * [[CollectionsTransform.UnsupportedOnTarget]]. */
  private var unsupportedOpTpe: TypeRepr = TypeRepr.NoType
  private var unsupportedOpSym: SymId    = SymId.None
  private var stringTpe: TypeRepr        = TypeRepr.NoType

  /** `java.lang.Object` as THIS run's symbol — the top of java's reference hierarchy, and the one
    * type an argument can carry that conforms to no scala element type at all ([[objectProbe]]). */
  private var objectSym: SymId = SymId.None

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

  /** every member this run held back because it OVERRIDES A CLASS FILE — see
    * [[classFileOverridesIn]]. EMPTY for a program that extends no unconverted java type, which is
    * what makes this a no-op by arithmetic exactly as an unrestricted scope is. */
  private var retainedOverrides: Set[SymId] = Set.empty

  /** …plus the PARAMETER symbols of those members, because a tree that says `java.util.Collection`
    * over a symbol that says `JavaCollection` is the lie [[mapSignatures]] already refuses to
    * write for the scope. Held apart from [[classFileOverrides]] so the boundary check keys on
    * MEMBERS and nothing else. */
  private var retainedOwners: Set[SymId] = Set.empty

  /** …read back, for [[scopedOut]]'s reason and with a DIFFERENT classification: the scope's seam
    * names a manifest key, and this one names nothing a port can edit (§4.56). */
  def classFileOverrides: Set[SymId] = retainedOverrides

  /** the union — every declaration whose type this run reads LITERALLY, whichever refusal held it.
    * `isEmpty` is the pre-refusal code path by arithmetic, which both halves need. */
  private def literal(s: SymId): Boolean = excluded(s) || retainedOverrides(s) || retainedOwners(s)
  private def literalEmpty: Boolean      = excluded.isEmpty && retainedOverrides.isEmpty

  /** the declaration → the scope ENTRY that admitted it, for `Reason.Configured`'s key (§4.575:
    * the key is the manifest entry VERBATIM, because it is the string an agent edits). */
  private var admittedBy: Map[SymId, String] = Map.empty

  private var report: PolicyReport = PolicyReport.empty

  /** the setting every retarget finding is filed under — the string an agent greps for (§4.575). */
  private val RetargetSetting = "CollectionsTransform(retarget) entry"

  /** …and the same for a reified carrier. */
  private val CarrierSetting = "CollectionsTransform(reifiedCarriers) entry"

  /** …and for a reflective sink. */
  private val SinkSetting = "CollectionsTransform(reflectiveSinks) entry"

  /** …and for a collection family entry. */
  private val FamilySetting = "CollectionsTransform(families) entry"

  /** the carriers that actually RUN — what the port declared plus the one java guarantees. A `val`,
    * so [[preservesTypeArgsOf]], the recorder and the fingerprint cannot disagree. */
  private val effectiveCarriers: Set[String] = reifiedCarriers ++ CollectionsTransform.UniversalCarriers

  /** …resolved to THIS program's symbols, once per run. Read off `program.symbols` and not off the
    * mapping, for the reason [[unmappedSupertypeSyms]] states: a carrier is a type this phase leaves
    * alone, so its symbol keeps the id it arrived with. EMPTY where the program names none of them,
    * which is what makes every arm below a no-op by arithmetic. */
  private var carrierSyms: Set[SymId] = Set.empty

  /** the REFLECTIVE SINKS this program actually names, as this program's own symbols. EMPTY where
    * the port declares none, which is what makes the egress bridge a no-op with no code path. */
  private var sinkSyms: Set[SymId] = Set.empty

  /** `JavaCollections.Reified.toJavaValue` — the EGRESS bridge (K21 face 1). Minted like every
    * other `Reified` member: nothing in a java program declares it. */
  private var toJavaValueSym: SymId = SymId.None

  /** every (sink callee, declared sink FQN) the egress bridge actually fired on, drained into
    * `decisions.tsv` at the end of the run. A per-SITE rewrite recorded per DECLARATION (§5.1). */
  private val bridgedSinkCallees = collection.mutable.Set[(SymId, String)]()

  /** …and every external callee with an OPAQUE formal this port has NOT declared a sink, keyed by
    * (callee, JAVA FILE) with the earliest site in that file — the review list [[opaqueEgress]]
    * exists to publish.
    *
    * The FILE is in the key and it is not redundancy: `boundary` reports one row per CALLEE and
    * filters to the units this module emits (`ENGINE-LIMITS.md` D2), and those two steps only
    * compose if the choice of site is made AFTER the filter. One global origin per callee makes a
    * dependent's row vanish whenever its base reaches the same callee from an earlier path. */
  private val opaqueEgressSites = collection.mutable.Map[(SymId, String), Origin]()

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
    }) ++ PolicyReport.fromBindings(boundCarriers.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, CarrierSetting, k, b.forget)
    }) ++ PolicyReport.fromBindings(boundFamilies.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, FamilySetting, k, b.forget)
    }) ++ PolicyReport(
      retarget.keys.toList.sorted.filter(typeMap.contains).map { k =>
        PolicyFinding(name, RetargetSetting, k, PolicyIssue.Malformed,
          s"`$k` already has a COLLECTION mapping (-> ${targetOf(k)}), which retypes its call " +
            "shapes and bridges its slots as well as moving the type. A retarget entry does only " +
            "the last of those, so the two answers are not refinements of one another — the entry " +
            "is ignored and the collection mapping stands")
      })

  override def run(program: Program): Program =
    // the class index [[classTparams]] reads — built once, from the program as PARSED, because a
    // type parameter's symbol is one thing this phase never moves.
    classDefsBySym = program.units.flatMap(StandardTraversal.allClassDefs(_)(using program))
                            .map(cd => cd.symbol -> cd).toMap
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
      program.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(mint(nm, fqn))
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

  /** the rest of [[run]], over the symbol table the bridge renames produced.
    *
    * A separate method for one reason and not for tidiness: `given Program` is established from the
    * symbol table, the rename REPLACES that table, and a second `given` in one scope is an
    * ambiguity rather than a shadow. Everything below reads the renamed program. */
  private def finishRun(program: Program, symbols: SymbolTable): Program =
    given Program = program.rebuilt(symbols = symbols)
    ownedSym = summon[Program].owned
    val units    = program.units.map(u =>
      dropSubsumedParents(
        restoreUninheritableParents(u, restoreExcluded(u, StandardTraversal.mapClassDef(this, u)))))
    val symbols2 = mapSignatures(symbols) // retype signatures too
    // …and the MODIFIER the re-parenting invalidated (K28). Decided over `symbols2` because
    // `graph.signatureOf` reads a symbol's `descriptor` — the frontend's, taken from the parser
    // before any retyping — so the two tables are asked in java's own spelling either way; applied
    // here rather than inside `mapSignatures` because that function's contract is about TYPES and
    // this moves a flag, and folding them would make one function answer two questions.
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
    // …and the members the minted parent declares, LAST: they are already scala-shaped, so
    // `mapSignatures` has nothing to do to them and `strippedOverrides` must not be given the
    // chance to take a modifier every one of them requires.
    val (units2, synthesised) = synthesiseBridges(units, symbols3)
    program.rebuilt(units2, SymbolTable(symbols3.all ++ synthesised))

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
    if literalEmpty then mapped
    else
      val body = CollectionsTransform.spine(orig.body, mapped.body, orig.symbol).map {
        case (o: Tree.ClassDef, m: Tree.ClassDef) => restoreExcluded(o, m)
        case (o: Tree.DefDef, m: Tree.DefDef)     => if literal(o.symbol) then o else m
        case (o: Tree.ValDef, m: Tree.ValDef)     => if literal(o.symbol) then o else m
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
      // the members THIS class's retained parents declare that their targets cannot carry — filled
      // while the parents are decided, because that is the one place both halves are in hand.
      val unimplementable = collection.mutable.Set.empty[CollectionsTransform.MemberSig]
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
              headSym(tpeOf(m)).flatMap(summon[Program].symbolOf).map(_.fullName)
                .flatMap(CollectionsTransform.UnsupportedOnTarget.get)
                .foreach(unimplementable ++= _)
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
        // …and the member half of the same refusal, under BOTH of its conditions — see
        // `declaresUnimplementable` (this really is the interface's member) and `brokenByMapping`
        // (the phase can point at what it broke). Either alone refuses a member java runs.
        case (_, m: Tree.DefDef) if unimplementable.nonEmpty =>
          val broken =
            if declaresUnimplementable(m, unimplementable.toSet) then brokenByMapping(m) else scala.None
          broken.fold(m)(refuseOnTarget(m, orig, _))
        case (_, m)                               => m
      }
      mapped.copy(parents = parents, body = body)

  /** A PARENT this phase minted that another parent this phase minted already SUBSUMES is DROPPED —
    * `ENGINE-LIMITS.md` K28.1.
    *
    * [[restoreUninheritableParents]]'s mirror: that one keeps JAVA's clause because the target
    * cannot BE a parent, this one removes the phase's OWN clause because a second target of its own
    * carries the relation java wrote it for. `class OrderedMap<K,V> implements Map<K,V>,
    * Iterable<Map.Entry<K,V>>` is emitted `extends mutable.Map[K,V] with JavaIterable[(K,V)]`, and
    * that class can never compile: `iterator(): JavaIterator[…]` and `iterator: Iterator[…]` are one
    * name at two arities and scala has ONE namespace, so `CLAUDE.md` §4.5's sentence — *the conflict
    * is in the parents* — arrives at a MINTING rather than at a shim's design.
    *
    * WHICH pairs subsume is [[CollectionsTransform.SubsumesShim]] and WHETHER this class's two
    * clauses agree on the element is [[carriesElement]]; both are decided in
    * [[declaredParentKinds]], where the java types are still in hand, and carried on
    * [[MintedParents.subsumed]] so that the emitted `extends` clause and the record every other
    * reader of this phase consults cannot disagree. That is not bookkeeping: `mintedSourceKind`
    * reads `shims` to answer *does a value of this class already conform at a shim-typed slot*, and
    * after the drop it does not — so removing the entry there is exactly what makes [[coerce]] wrap
    * the seams this drop opens, at the slot, where a reader can act on them.
    *
    * A `StandardTraversal` walk rather than a `body` recursion (§3): a method-LOCAL class is a
    * `Tree.ClassDef` in a `BlockStatement`, and [[declaredParentKinds]] reaches one, so anything
    * acting on its answer must too. An ANONYMOUS body needs nothing here — java writes its ONE
    * supertype at the `new`, so it can never hold both clauses and `subsumed` is empty for it by
    * arithmetic.
    *
    * A no-op wherever nothing is subsumed, which is every port in the corpus but one. */
  private def dropSubsumedParents(u: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    if parentClash.forall((_, mp) => mp.subsumed.isEmpty) then u
    else
      def tpeOf(x: Term | TypeTree): TypeRepr = x match
        case tt: TypeTree => tt.tpe
        case t: Term      => t.tpe
      val ph = new Phase:
        def name: String = "collections/drop-subsumed-parents"
        override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
          val drop = parentClash.get(cd.symbol).map(_.subsumed).getOrElse(Map.empty)
          if drop.isEmpty then cd
          else
            val kept = cd.parents.filter { pt =>
              headSym(tpeOf(pt)).flatMap(p.symbolOf).map(_.fullName).forall(!drop.contains(_))
            }
            if kept.sizeIs == cd.parents.size then cd
            else
              cd.parents.filterNot(kept.contains).foreach { pt =>
                val gone = TirPrinter.tpe(tpeOf(pt), TirPrinter.Style.canonical)
                val by   = headSym(tpeOf(pt)).flatMap(p.symbolOf).map(_.fullName).flatMap(drop.get)
                // …on THIS phase's buffer, not on the walk's throwaway one. `Phase.record` is an
                // instance method, so a bare `record` inside `new Phase` files the decision against
                // a phase nobody drains — the parent really is dropped and `decisions.tsv` says
                // nothing, which `NoteCoverageCheck` cannot report either because it compares the
                // notes it CAN see against the decisions it CAN see.
                CollectionsTransform.this.record(Decision(
                  kind       = Decision.Kind.SubsumedParent,
                  subject    = cd.symbol,
                  subjectFqn = p.symbolOf(cd.symbol).map(_.fullName).getOrElse(gone),
                  detail = Map(
                    "dropped"     -> gone,
                    "subsumed-by" -> by.getOrElse("?"),
                    "why" -> ("java related this class's two `implements` clauses at ONE MEMBER " +
                      "spelled two ways, and the parent this mapping minted for the other clause " +
                      "already carries that relation. Minting both would declare one name at two " +
                      "arities, which scala's single namespace cannot hold and which no repair at " +
                      "the member can fix (CLAUDE.md §4.5). A value of this class meeting a slot " +
                      "typed at the dropped shim is coerced there instead"),
                  ),
                  reason = Reason.Universal("subsumed-minted-parent(§4.5, K28.1)"),
                  origin = cd.origin,
                ))
              }
              cd.copy(parents = kept)
      StandardTraversal.mapClassDef(ph, u)

  /** IS THIS THE INTERFACE'S MEMBER, or a method that merely shares its name?
    *
    * The refusal below is owed to a member the RETAINED PARENT declares, so the test is the
    * parent's own signature — `java.util.Map.Entry` declares exactly `setValue(V)` — and never the
    * bare name. A class implementing that interface is free to declare `setValue(int, int)` beside
    * it; java resolves the two separately and the interface says nothing about the second. Matched
    * by name alone, that method's body was replaced by a throw as well: a method java runs, the
    * port refuses, with a green compile and no count moving anywhere (CLAUDE.md §3).
    *
    * See [[CollectionsTransform.MemberSig]] for why `arity` is both the whole of the signature
    * available here and enough of it. */
  private def declaresUnimplementable(d: Tree.DefDef, sigs: Set[CollectionsTransform.MemberSig])(
      using p: Program): Boolean =
    p.symbolOf(d.symbol).exists(s =>
      sigs.contains(CollectionsTransform.MemberSig(s.name, d.paramss.map(_.size).sum)))

  /** …AND CAN THIS PHASE POINT AT WHAT IT BROKE? The second condition, and the one that keeps the
    * refusal a translation rather than a policy.
    *
    * `Map.Entry.setValue` is an optional operation, so throwing is a CONFORMING implementation —
    * but only for an entry that genuinely cannot perform the write. An entry that stores its own
    * value performs it perfectly, java runs it, and nothing this phase did touches the body:
    * substituting a throw there makes the port fail where java succeeded. Both bodies are the same
    * five letters and the difference is entirely what the body DOES, so the licence has to be read
    * off the body and not off the declaration.
    *
    * §4.56 says how: a phase may only conclude something about a member from what the PHASE ITSELF
    * did to it. The mapping's own record answers exactly that — a receiver this phase retyped to a
    * target in [[CollectionsTransform.UnsupportedOnTarget]], carrying a call to one of the members
    * that target cannot express, is a reference the mapping REMOVED, and it is the whole reason
    * there is no body left to emit. Read off the MAPPED tree, because the untranslated one still
    * names the java type at every position.
    *
    * Returns the reference it found, so the refusal can say which one it was — a decision that does
    * not name the call it replaced sends its reader back to the java to guess. */
  private def brokenByMapping(d: Tree.DefDef)(using p: Program): Option[String] =
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

  /** THE OTHER HALF OF A RETAINED PARENT — the member that parent declares and the target cannot.
    *
    * Keeping java's parent makes the class's `extends` clause legal; it does not make the class
    * COMPLETE. `java.util.Map.Entry` declares `setValue`, so the emitted class must still implement
    * it, and the body upstream wrote is a write-through call on a receiver this phase retyped to a
    * `Tuple2` — which has no such member. **Dropping the member is not open to the port either**:
    * a `dropMethods` key removes a member the retained parent declares and leaves the class
    * abstract, and that failure is invisible until the port reaches 0 typer errors, because
    * `RefChecks` does not run before then (CLAUDE.md §3). Measured exactly that way — the drop
    * traded one `Not Found` for one `needs to be abstract`.
    *
    * So the answer is JAVA'S OWN, and it is a contract rather than a stand-in: `Map.Entry.setValue`
    * is an **optional operation**, documented to throw `UnsupportedOperationException` where the
    * backing map does not support the write. A ported entry whose map is not reachable from the
    * call is precisely that entry, so the port emits the refusal the interface prescribes. It is
    * the same refusal K2 has always made, expressed in code instead of as a compile error — and it
    * is the opposite of the alternative K2 rejects: a `SimpleEntry` would compile and write to a
    * DETACHED COPY, succeeding while changing nothing, which is CLAUDE.md §4.4's defect class.
    * Throwing is louder than java, never quieter, and it is counted at the slot.
    *
    * The LOOP-REACHABLE case never arrives here: `writeThroughEntries` has already turned
    * `e.setValue(v)` inside `for (e : m.entrySet())` into the map's own `put`, so what is left is
    * exactly the case with no loop and no map.
    *
    * **Both conditions are the caller's and neither is optional** — [[declaresUnimplementable]] (a
    * member the parent really declares, by signature) and [[brokenByMapping]] (a body this phase
    * really broke, `broke` being what the latter found). Either alone refuses a member java runs. */
  private def refuseOnTarget(d: Tree.DefDef, owner: Tree.ClassDef, broke: String)(using p: Program): Tree.DefDef =
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
        // the reference the mapping REMOVED, verbatim — the licence for the substitution and the
        // one fact a reader cannot recover from the emitted throw. A body with no such reference
        // is not substituted at all.
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

  /** `StandardTraversal.mapSymbols`, minus the symbols the scope held back — so an excluded
    * declaration's SIGNATURE stays exactly as the frontend read it, which is what the restored tree
    * above says it is. The two must agree: a tree that says `java.util.List` over a symbol that
    * says `Buffer` is a lie every later reader believes. */
  private def mapSignatures(tbl: SymbolTable)(using p: Program): SymbolTable =
    if literalEmpty then StandardTraversal.mapSymbols(this, tbl)
    // …and the same OWNERSHIP guard `mapSymbols` carries: an external member's signature is a fact
    // about a class file and this phase cannot move it. Without it the scoped path would retype
    // exactly the formals `coerce` and the boundary count now read (K15).
    else tbl.all.foldLeft(tbl) { (t, s) =>
      if literal(s.id) || !p.owns(s.id) then t
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

  // -------------------------------------------------------------------------
  // …and the refusal NO POLICY ASKED FOR — a member that overrides a CLASS FILE
  // -------------------------------------------------------------------------

  /** the anchor a held member is held BY — `<fqn>#<member>`, for the decision's own detail. */
  private var retainedAnchors: Map[SymId, String] = Map.empty

  /** Fill [[retainedOverrides]]: every member whose formals this phase MAY NOT MOVE, because the
    * declaration it overrides lives in a compiled class file.
    *
    * ==The defect==
    * `class BitFieldSet<E> extends java.util.AbstractSet<E>` declares `containsAll(Collection<?>)`
    * and opens it `if (!(c instanceof BitFieldSet)) return super.containsAll(c);`. The mapping
    * moves the formal to the shim; the PARENT is a java class the mapping does not cover, so its
    * `containsAll` still takes a `java.util.Collection`. The emitted member then overrides NOTHING
    * and its own `super` call cannot compile — which is `CLAUDE.md` §4.56's "an unowned symbol's
    * SIGNATURE is a fact about a class file" read at an OVERRIDE rather than at a call.
    *
    * ==The test is STRUCTURAL, and every conjunct earns its place==
    *   - `Flags.isOverride` — the frontend's own answer, computed from the parser's resolved
    *     hierarchy (`SpoonTir.overridesInherited`), which is why the emitted members carry
    *     `override` at all. Without it the anchor test below fires on EVERY member of every class
    *     with an unparsed parent: `ExternalSurface.mayDeclare` answers YES for an unknown type, on
    *     purpose, so `BitFieldSet.noneOf` — a static this class invented — would be held back too.
    *   - `mentionsMapped` — the signature really moves. Without it the set takes
    *     `toString`/`equals`/`hashCode`, whose bodies touch retyped collections and whose
    *     signatures do not.
    *   - `OverrideGraph.overridden` EMPTY — the program declares NO ancestor with this signature,
    *     so whatever the frontend resolved the override against is a class file. This is the
    *     conjunct that makes the answer exact rather than approximate, and dropping it in favour of
    *     the closure's own `externalAnchors` was MEASURED at 69 → 113: `ExternalSurface.mayDeclare`
    *     answers YES for an unparsed type on purpose (an over-refusal is the safe direction for a
    *     RENAME), so `java.util.function.Function` "might declare" `getAfterDependents` and 104
    *     members were held over an interface this program declares itself.
    *   - an EXTERNAL ANCESTOR OF THE OWNER THAT THE MAPPING DOES NOT COVER, and that could declare
    *     this signature. This is the negative case, and it is the one that decides correctness on
    *     every OTHER port: a class extending a MAPPED collection (`extends java.util.ArrayList<X>`)
    *     emits the SHIM as its parent, so the shim's own members are already in shim shape and
    *     holding the override back would break it in the other direction. Read from
    *     `typeMap`/`retarget` — the phase's own record (§4.56) — and never from a name.
    *
    * ==and what it holds is the member AND EVERY OVERRIDER BELOW IT==
    * A signature change applies to all of a component or none of it (`DESIGN.md` §8.5), and the
    * conjuncts above are asked of the TOP of the component by construction — `overridden` is empty
    * exactly where nothing this program declares is above it. `overriders` is the rest, and it is
    * the DOWNWARD walk only: every member of it is a declaration this program owns, so no
    * `mayDeclare` guess enters anywhere.
    *
    * EMPTY for a program whose classes extend nothing unconverted, which is what makes this a
    * no-op by arithmetic rather than by a branch. */
  private def applyClassFileOverrides(p: Program): Unit =
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
        // …and the SHIM half of that list decides it. Where a candidate declarer is a type the
        // mapping COVERS, the emitted parent is the shim, the shim's own member is already in shim
        // shape, and holding the override back would break it in the other direction. Only when
        // EVERY candidate stayed java is there a java signature to be held to.
        val kept = if declarers.exists(coveredExternally) then Nil else declarers
        if kept.nonEmpty then
          val label = kept.sorted.map(fqn => MemberKey(fqn, sig.map(_.name).getOrElse("?")).render).mkString(", ")
          (m :: graph.overriders(m)).filter(owned).filter(spliceable(graph))
            .foreach { x => held += x; anchor += (x -> label) }
    }
    retainedOverrides = held.toSet
    retainedAnchors   = anchor.toMap
    // a held member's PARAMETER symbols go with it: `restoreExcluded` splices the original
    // `Tree.ValDef`s back, and a symbol table saying otherwise is the lie `mapSignatures` already
    // refuses to write for the scope.
    retainedOwners = p.symbols.all.collect { case s if retainedOverrides(s.owner) => s.id }.toSet

  /** is this external type one the phase's OWN tables move — so that the emitted parent is a shim
    * and an override of its members belongs in shim shape? Read from the tables, never from the
    * name (§4.56). */
  private def coveredExternally(fqn: String): Boolean =
    typeMap.contains(fqn) || effectiveRetarget.contains(fqn)

  /** CAN [[restoreExcluded]] ACTUALLY REACH THIS MEMBER? — the conjunct without which the refusal
    * is a note that LIES.
    *
    * `restoreExcluded` splices held-back members back along the DECLARATION SPINE of a
    * `Tree.ClassDef`, which is right for the scope and is deliberately not a second traversal
    * (see its own doc). An ANONYMOUS class's body is not on that spine — it hangs off a
    * `Tree.New` inside a TERM — so a member held there keeps its MAPPED tree while
    * [[mapSignatures]] holds its SYMBOL literal: the two disagree, and the porter note claims a
    * signature the emitted `def` does not have.
    *
    * Measured on liqp, which is at 0 errors: `new ThreadLocal<Map<String,Object>>(){ …
    * initialValue() … }` recorded the decision and emitted `mutable.Map` under it. Holding it
    * PROPERLY is not the fix available here either — the body returns a `TrieMap`, so a literal
    * `java.util.Map` result would be a fresh error on a green port — so the honest answer is that
    * this member is out of the refusal's reach and stays retyped, exactly as before.
    *
    * An anonymous-class symbol has no `Definition` (§4.56's own note is that ownership is stronger
    * than "has a definition" for precisely this reason), so the test is structural. */
  private def spliceable(graph: OverrideGraph)(m: SymId)(using p: Program): Boolean =
    p.definitionOf(graph.ownerOf(m)).exists(_.isInstanceOf[Tree.ClassDef])

  // -------------------------------------------------------------------------
  // …and the MODIFIER a re-parenting invalidated — `ENGINE-LIMITS.md` K28
  // -------------------------------------------------------------------------

  /** Drop `Flags.isOverride` from every member whose only anchor was a parent THIS PHASE MOVED and
    * whose emitted parent does not declare it.
    *
    * ==The defect==
    * The frontend's `override` is an honest statement about JAVA — `OrderedMap#containsKey` really
    * does override `java.util.Map#containsKey`. Re-parenting the class onto
    * `scala.collection.mutable.Map` moves the far side of that statement without touching the
    * modifier, and `E037 overrides nothing` / `E038 has a different signature` is what scalac says
    * about a member whose name, formals and body are all correct. It is `CLAUDE.md` §1's *an
    * obligation the ENGINE'S OWN TRANSLATION created*: no manifest key produced it and none can
    * discharge it. And it is UNMEASURABLE until a port reaches zero typer errors, because
    * `RefChecks` does not run before then (§3) — which is why this arrives one wave after the port
    * went green and not at any point in the twelve before it.
    *
    * ==Four conjuncts, each closing a different way to be wrong==
    *   - `Flags.isOverride` AND OWNED. Nothing to strip otherwise, and an unowned symbol's flags are
    *     a fact about a class file (§4.56).
    *   - **this phase RE-PARENTED the owner.** [[parentClash]] is the record of exactly that, and it
    *     already excludes the three shapes that KEPT java's parent (a declaration the scope held
    *     back, a target that cannot BE a parent, a parent the mapping does not cover). Where it has
    *     no entry the class still extends what java wrote and every modifier on it is still true.
    *   - **no PROGRAM-DECLARED ancestor declares the member** ([[programAncestorDeclares]]). The
    *     port emits the far side itself, so the modifier is required whatever the minted parent
    *     does — this is the conjunct that keeps an ordinary override inside the library's own
    *     hierarchy from being stripped because its owner happens to implement a JDK collection. It
    *     is deliberately NOT `OverrideGraph.overridden`, and the difference is measured: see that
    *     function's own note.
    *   - **no ancestor whose emitted surface this phase cannot ANSWER FOR could declare it.** Two
    *     halves, because two kinds of ancestor are invisible in different ways. An unparsed PARENT
    *     is `externalAncestorsOf` filtered by [[tabulatedTarget]] — read through
    *     `ExternalSurface.default`, whose unknown side answers YES on purpose. And `java.lang.Object`
    *     is above every java type whether or not the tree says so (the frontend filters it out of
    *     every parent list), so `equals`/`hashCode`/`toString`/`clone` have to be asked of it
    *     separately or all four lose a modifier scala requires.
    *
    * The fifth test is [[CollectionsTransform.OverridesTarget]] / [[CollectionsTransform.OverridesShim]]
    * — does the emitted parent declare this member at all — and its two error directions are BOTH
    * LOUD, which is what licenses a table for it. See that table's own note.
    *
    * ==What it deliberately does NOT touch==
    * The `E164` family. A member that reaches its target's own declaration and disagrees about the
    * RESULT (`put(K,V): V` against `Option[V]`, `iterator(): JavaIterator` against `=> Iterator`) is
    * listed in the table, keeps its modifier, and goes on reporting — no modifier repairs it and
    * silencing the report would hide a real translation this phase still owes.
    *
    * EMPTY for a program whose classes extend nothing this phase maps, so it is a no-op by
    * arithmetic rather than by a branch. */
  private def strippedOverrides(before: SymbolTable)(using p: Program): Set[SymId] =
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

  /** did this phase move `fqn` to a target THIS FILE TABULATES the surface of?
    *
    * Deliberately NOT [[coveredExternally]], which is the same question one notch wider and which
    * was measured wrong here. That one answers *did the phase move this type at all*, and its second
    * disjunct is `retarget` — a per-library table whose targets (`scala.math.Ordering`,
    * `scala.concurrent.duration.Duration`, whatever a port names) this file holds no surface for. A
    * parent moved into one is a parent whose members are as unknown as any unparsed type's, so
    * filtering it OUT of the anchors says *nothing above this member could declare it* on no
    * evidence at all: `Attributes implements Comparator<Attribute>` lost the `override` on its
    * `compare`, which `scala.math.Ordering` really does declare (0 errors, because that declaration
    * is ABSTRACT and scala's modifier is optional there — one moved digest on a port at zero, and
    * the only instrument that saw it).
    *
    * So the test is the POSITIVE one §4.56 asks for — *what did the phase itself do, and can it
    * answer for the result* — and everything else anchors. A `Kind` with no row (`Entry`, `Opt`) is
    * unanswerable for the same reason and takes the same arm, which is why this reads the two
    * surface tables rather than `typeMap`'s membership. */
  private def tabulatedTarget(fqn: String): Boolean =
    typeMap.get(fqn).exists { (tgt, k) =>
      if CollectionsTransform.standaloneTargets(tgt) then CollectionsTransform.OverridesShim.contains(tgt)
      else CollectionsTransform.OverridesTarget.contains(k.toString)
    }

  /** does an ancestor THIS PROGRAM DECLARES declare `sig` — by NAME AND ARITY, deliberately looser
    * than the override edge?
    *
    * `OverrideGraph.overridden` is the exact answer and it is the WRONG one here, which is the sort
    * of thing only a run says. Its edges are keyed on `Descriptor`, the SOURCE-LEVEL parameter
    * spelling (`ENGINE-LIMITS.md` D1's identity), and a java interface may declare a member at one
    * type-parameter NAME while its implementor declares the same member at another — permuting the
    * clause is ordinary java and one library does it (`I<M, S, K>` declaring `addItem(K, int)`,
    * implemented by `B<K, S, M> implements I<K, S, M>` declaring `addItem(M, int)`). Two spellings,
    * one member, and `overridden` answers EMPTY.
    *
    * That answer is harmless where it is asked as *may I rename this* and wrong where it is asked as
    * *is this modifier still true*: **6 members lost an `override` they were entitled to keep**, and
    * nothing reported it, because the parent's declaration is ABSTRACT and scala's modifier is
    * optional at an implementation. No error, no moved count, six moved digests — §5's
    * over-approximation with the one instrument that can see it.
    *
    * So the question is asked at the LOOSER key, and the direction of its error is refusal: a member
    * that shares a name and an arity with an ancestor's keeps its modifier, which is at worst the
    * `E037` this pass was written to remove and at best the truth. That is `OverrideGraph`'s own
    * convention for a missing descriptor, read at a descriptor that is present and disagrees. */
  private def programAncestorDeclares(graph: OverrideGraph, owner: SymId,
                                      sig: OverrideGraph.Signature)(using Program): Boolean =
    graph.ancestorsOf(owner).exists { a =>
      graph.membersOf(a).exists(m =>
        graph.signatureOf(m).exists(o => o.name == sig.name && o.arity == sig.arity))
    }

  /** does ANY parent this phase minted for `cls` declare `sig`? — the far side of the override, read
    * off the two tables the phase holds for its own targets.
    *
    * OR across the parents and not AND, because a class routinely has several — java's interfaces
    * are small and orthogonal (§4.5) and `class OrderedMap implements Map<K,V>, Iterable<Entry<K,V>>`
    * is the shape this phase was written from — and ONE parent declaring the member is enough to
    * make the modifier true. Asked as AND, a member the shim declares would lose its `override`
    * because the scala collection beside it does not, and the strip would be deciding from the last
    * parent it happened to look at. */
  private def mintedParentDeclares(cls: SymId, sig: OverrideGraph.Signature): Boolean =
    parentClash.get(cls).exists { mp =>
      mp.kinds.exists(k => CollectionsTransform.OverridesTarget.get(k.toString).exists(_.exists(_.matches(sig)))) ||
        mp.shims.exists(s => CollectionsTransform.OverridesShim.get(s).exists(_.exists(_.matches(sig))))
    }

  /** DECISION PROVENANCE for [[strippedOverrides]].
    *
    * One row per member, which is one row per DECLARATION — this decision is about a declaration and
    * not about a site, so §5.1's per-declaration rule is met by construction. `Reason.Universal`,
    * because there is no key: the java said `implements java.util.Map`, the engine chose the target,
    * and telling the reader to edit a scope would cost them the session §4.45 is about.
    *
    * `parent=` is the reader's actual next question. `overrides nothing` is a sentence about a type,
    * and the type is one the java file never names. */
  private def recordStrippedOverrides(stripped: Set[SymId], before: SymbolTable)(using p: Program): Unit =
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
  // …and the SURFACE the re-parenting OWES — `ENGINE-LIMITS.md` K28.1
  // -------------------------------------------------------------------------

  /** one bridge this run will build: the class, the kind it was minted at, the mapped parent's java
    * type ARGUMENTS, the row, and the java member the body delegates to (`SymId.None` for a row
    * that has none — see [[CollectionsTransform.Bridged]]). `rename` is [[CollectionsTransform.CapturedByTarget]]'s
    * answer, carried rather than re-derived so the rename pass and the body builder cannot
    * disagree about which member the body is allowed to name. */
  private final case class Bridge(cls: SymId, kind: Kind, args: List[TypeRepr],
                                  row: CollectionsTransform.Bridged, java: SymId, rename: Boolean)

  private var bridges: List[Bridge] = Nil

  /** the JAVA types the mapping sends to a given target — the inverse of `typeMap`.
    *
    * `subsumed` is keyed on the TARGET (`balticporter.runtime.JavaIterable`), and an override
    * anchor is spelled with the JAVA type the class file declares (`java.lang.Iterable`), so the
    * two have to be joined through the phase's own table rather than by a name that looks alike. */
  private def shimSource(target: String): Set[String] =
    typeMap.collect { case (fqn, (tgt, _)) if tgt == target => fqn }.toSet

  /** how many type arguments a kind's target needs before a bridge can name its key, value or
    * element type. A RAW clause supplies none, and inventing `java.lang.Object` for them would be
    * §4.6's fabricated fact at the emitted signature — so the whole class declines, counted. */
  private def kindArity(k: Kind): Int = if k == Kind.Map then 2 else 1

  /** WHICH bridges this run owes — one row per (class, row) the table names and the class can
    * answer.
    *
    * Asked of `declared` and never of `kinds`, which is what puts the synthesis on the BASE and not
    * on each subclass. A dependent class inherits its ancestor's minted parent AND the bridges over
    * it, so a second copy on the subclass would be an override of a member the subclass has no java
    * member of its own to delegate to — one FQN, two definitions of one surface, which is §1.5's
    * shape read inside a single module. The four subclass owners in the corpus reach zero this way
    * with nothing emitted on any of them. */
  private def planBridges(p: Program): List[Bridge] =
    if parentClash.isEmpty then return Nil
    given Program = p
    val graph = OverrideGraph.build(p)
    def sigOf(m: SymId): Option[OverrideGraph.Signature] = graph.signatureOf(m)
    /** the delegate, on THIS class and on no ancestor of it.
      *
      * The second half is what puts one bridge on the component rather than one per subclass: a
      * class that inherits `put` from a program ancestor inherits the bridge over it too, and a
      * second `override def put` on the subclass would be one surface with two definitions. It also
      * settles the interface case structurally — a java interface `extends Map<K,S>` declares none
      * of these, so every row declines and the type is not the implementor. */
    def ownMember(cls: SymId, want: ExternalSurface.Member): Option[SymId] =
      if graph.ancestorsOf(cls).exists(a => graph.membersOf(a).exists(m => sigOf(m).exists(want.matches)))
      then scala.None
      else
        // …and where the key names SEVERAL, java's own resolution order picks. `add(E)` sits beside
        // `add(E...)` and both have arity 1, so a (name, arity) key names two members — §4.55's
        // over-approximate key, met at a delegate — and java admits the fixed-arity candidate in
        // phase 1 or 2 and the pack only in phase 3 (JLS 15.12.2), so java never binds `add(e)` to
        // the pack while the scalar exists. It also could not compile if it did: the port emits a
        // `T...` formal it DECLARES as `Array[T]` (§4.4), so a bridge passing one element reads
        // `Found: E / Required: Array[E]` — measured, exactly once, on the one corpus class that
        // declares both. A LAST-ARRAY candidate is still taken when it is the only one, because at
        // an element type that really is an array it is the fixed-arity member.
        val cands = graph.membersOf(cls).filter(m => sigOf(m).exists(want.matches) && !literal(m))
        def packs(m: SymId): Boolean = sigOf(m).flatMap(_.descriptor).exists(_.params.lastOption match
          case Some(Param.Arr(_)) => true
          case _                  => false)
        cands.find(m => !packs(m)).orElse(cands.headOption)
    parentClash.toList.sortBy((c, _) => p.symbolOf(c).map(_.fullName).getOrElse("")).flatMap { (cls, mp) =>
      mp.declared.distinct.flatMap { (k, args) =>
        val rows = CollectionsTransform.BridgedTarget.getOrElse(k.toString, Nil)
        // …a type that declares NONE of the delegates is not the one implementing the interface —
        // an interface that merely widens it, or a subclass whose ancestor holds every member. It
        // owes nothing and is not reported as owing anything, which is the difference between a
        // refusal population and noise (§3).
        val found = rows.filter(_.from.nonEmpty).flatMap(r => r.from.iterator.flatMap(ownMember(cls, _)).nextOption())
        if rows.isEmpty || found.isEmpty then Nil
        else if args.sizeIs != kindArity(k) then
          // a RAW `implements Map` names no key and no value. Leaving the `E164` the compiler
          // already states is the honest arm; guessing the arguments is not.
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

  /** the refusal LANE — one row per bridge this run could not build, naming the guard (§3).
    *
    * On `collection-boundary`, where the other residues of this phase's own re-parenting already
    * are, and as its own `Issue` because the classification is a different one: the reader is not
    * being told about a slot whose two sides disagree, they are being told that the emitted class
    * is missing a member scalac will demand. */
  private def refuseBridge(p: Program, cls: SymId, k: Kind, guard: String, why: String): Unit =
    seam(s"minted-parent surface [$guard]", k.toString, why,
         Decision.originOf(p, cls), cls, CollectionBoundaryCheck.Issue.UnbridgedMember)

  /** RENAME every captured delegate out of the way, through §4.55's own machinery.
    *
    * `MemberRenamer` is the whole of the rename: it expands each request through its OVERRIDE
    * CLOSURE (so a subclass's `put` moves with its base's — a signature change applies to all of a
    * component or none of it), screens the closure for an external anchor (a member that also
    * overrides a class file may not be renamed at all, and the refusal says which type), reads
    * EFFECTIVE names PARENTS-FIRST, and files the `RenamedMember` decision every downstream reader
    * — the porter note, the port map's `name=`, the emitter's own clash passes — already knows how
    * to read. Writing a second renamer here would be two spellings of one act.
    *
    * `SuffixUntilFree` rather than `Refuse`: the body reads the delegate's name back OUT of the
    * symbol table, so a `$java$` that had to move once is still exactly nameable, while a refusal
    * would leave the `E164` this exists to close for the sake of a name nobody reads. The GROUP is
    * the owning class, so a class whose delegates cannot all move keeps every one of them — half a
    * bridged surface is a class that compiles less well than the one this started from. */
  private def renameBridgeDelegates(p: Program): SymbolTable =
    val wanted = bridges.filter(b => b.rename && b.java != SymId.None).map(_.java).distinct
    if wanted.isEmpty then return p.symbols
    val graph = OverrideGraph.build(p)
    val owners = bridges.filter(b => b.java != SymId.None).map(b => b.java -> b.cls).toMap
    // …the parents THIS PHASE removed from THIS class. Two sources, both the phase's own record
    // (§4.56): every java type the mapping RE-PARENTED the class away from, and every shim clause
    // `dropSubsumedParents` deleted because a minted kind already carried the relation (K28.1's
    // first commit). `java.lang.Iterable` is only ever in the second set, and only for the classes
    // whose clause was really dropped — which is why this is per REQUEST and not per call.
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
      // …the whole class, not the one member: see the group note above.
      bridges = bridges.filterNot(b => lost.contains(b.cls))
    renamed.symbols

  /** …and the SYNTHESIS, appended to each owning class's body.
    *
    * Appended rather than spliced at a position, because these members have no java counterpart to
    * sit beside — every one of them is a member the emitted PARENT declares and the java file does
    * not, so there is no source order to honour and JLS 12.5's ordering rule (§4.55) has nothing to
    * say about a `def`. */
  private def synthesiseBridges(units: List[Tree.ClassDef], symbols: SymbolTable)
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
    // …and the synthesised symbols are handed BACK rather than kept in a field: they are added to
    // the table AFTER `strippedOverrides` has run, so nothing in this phase ever has to ask "is this
    // one of mine" — the ordering answers it, and a field would be a second, staler way to say so.
    (units.map(u => StandardTraversal.mapClassDef(ph, u)), added.toList)

  /** ONE bridged member, as a tree.
    *
    * Every body here is a DELEGATION and that is the whole safety argument: the port's behaviour at
    * the member is java's own body, unchanged, reached under a new name. What the bridge adds is
    * the SHAPE conversion the parent asked for, and there are exactly four of them — `Option(x)` for
    * java's null-or-value (which is what `MapOps` means by `Option`, and java's own `get`/`put`
    * document the null), `{ x; this }` for a `Growable`/`Shrinkable` member whose java counterpart
    * answers a `boolean` nobody reads, `.asScala` for a shim result at a `scala.collection` slot,
    * and `{ x; () }` for a result the parent discards.
    *
    * The three `Kind.Seq` rows with no delegate are the exception and are documented where their
    * bodies live (`JavaCollections.buffer*`): java's `List` declares no counterpart, so there is no
    * java behaviour to reproduce and what is owed is scala's contract over the bridges beside them.
    *
    * `None` where the table names a row this builder has no arm for, which cannot happen for a row
    * in [[CollectionsTransform.BridgedTarget]] and is the honest arm rather than a crash if one is
    * ever added without its body. */
  private def buildBridge(b: Bridge, cd: Tree.ClassDef, symbols: SymbolTable,
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
  private def recordBridge(b: Bridge, sym: SymId, fqn: String, delegate: String, o: Origin): Unit =
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

  /** DECISION PROVENANCE for the `super` → `this` substitution (`ENGINE-LIMITS.md` K29).
    *
    * This one is recorded where the other `JavaCollections` rewrites are not, and the line between
    * them is §5.1's: an ordinary rewrite is a mechanical API mapping that the diff against the java
    * shows by itself (`xs.containsAll(c)` reads as `JavaCollections.containsAll(xs, c)` right
    * there). This one changes WHICH MEMBER java named — `super` became `this` — and the licence for
    * that is an argument about the JDK's own body, which no diff can carry.
    *
    * Per DECLARATION, and read out of the XREF rather than from a walk this phase would keep of its
    * own (§5.1 again, and the reason `Decision.declarationsUsing` exists). The answer is filtered to
    * the classes whose `super` this run actually substituted, which is exact rather than
    * approximate: a class that OVERRIDES the member resolves every non-`super` mention of it to its
    * own override, so a usage of the JDK SYMBOL inside one of those classes IS the `super` call. */
  private def recordSuperDefaults(using p: Program): Unit =
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

  /** DECISION PROVENANCE for [[applyClassFileOverrides]] — [[recordScopedOut]]'s row with the
    * other §1 classification, and the reason the two are separate kinds: a reader told to widen a
    * scope that does not exist has been sent after a key nothing in the port can supply (§4.45).
    *
    * (It sat above `recordSuperDefaults` as an ORPHAN doc block for as long as that function had
    * one of its own, so scaladoc attached neither to this — moved here, which is where it was
    * always about.) */
  private def recordRetainedSignatures(before: SymbolTable)(using p: Program): Unit =
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
          // …and the ORDER-KEEPING targets are a catalog row of their own (`JS-C42`), cited here
          // because the difference is discharged by the TABLE and not by a per-site decision: a
          // reference to `EnumMap` is lowered by the same arm as every other type, so making that
          // arm owe a consult would demand one at every type in every program. `cite` is the
          // phase-level surface for exactly this shape.
          if mentionsOrderedShim(now.info) then
            cite(balticporter.catalog.JS.C(42), s.fullName)
      }
    }

  /** does this signature mention one of the two ORDINAL-ORDER shims anywhere inside it?
    *
    * Read off the phase's own mapping (§4.56: what the phase DID, never what a name looks like) and
    * walked with `StandardTraversal.mapType` for the reason [[retargetKeysIn]] is — a hand-rolled
    * recursion that stopped at a `MethodType`'s parameters would answer "no" for every method. */
  private def mentionsOrderedShim(t: TypeRepr)(using Program): Boolean =
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

  // -------------------------------------------------------------------------------------------
  // THE THIRD REIFIED POSITION — a type ARGUMENT a third party reads out of the class file (K20)
  // -------------------------------------------------------------------------------------------
  //
  // K18 answered the two reified positions a java program WRITES — `x instanceof T` and `(T) x`.
  // This one is written nowhere: the occurrence is a type argument in a declaration, so a phase that
  // walked every `InstanceOf` and every `Typed` visits nothing, no coercion has anywhere to go
  // (there is no value — the argument is a type), and no slot disagrees with another. Every
  // instrument therefore reads clean, which is why the answer is at the TRAVERSAL: it is the one
  // place that knows it is about to descend into an argument.
  //
  // The value's bridge is NOT built here. Once the argument stays java's, the call that consumes the
  // carrier really does return java's type while the node claims a mapping target, and that is
  // exactly `externalProducer`'s seam — it wraps into a live view where one exists and COUNTS the
  // slot where none does. What changes for that arm is only `passesThrough`: the result type used to
  // OCCUR inside the carrier argument, so the call read as a generic pass-through and was suppressed.
  // With the argument left in java's namespace the occurrence is gone, and the same code that always
  // handled `readValue(json, HashMap.class)` handles `convertValue(v, MAP_TYPE_REF)`.

  /** WHICH type constructors' arguments this run must not move — the carriers, resolved to this
    * program's own symbols. `false` by arithmetic where the port declares none and the program names
    * no `java.lang.Class`, which is the §1(b) no-op with no code path. */
  override def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean =
    carrierSyms.nonEmpty && headSym(tc).exists(carrierSyms.contains)

  /** DECISION PROVENANCE for the preservation — one row per DECLARATION holding a carrier argument
    * this phase would otherwise have retyped.
    *
    * `recordScopedOut`'s reasoning, one position in: the row that would explain the line is the one
    * that is NOT there, and the diff against the java shows nothing because nothing changed. A
    * reader of `MAP_TYPE_REF: TypeReference[java.util.Map[String, Object]]`, sitting beside a method
    * that returns `mutable.Map`, is looking at the only java collection left in the file and has no
    * way to tell a deliberate preservation from a retyping the phase missed.
    *
    * Two reasons, because the fix lives in two different repositories (§4.45): a carrier the PORT
    * declared is `Configured` with the entry verbatim, and `java.lang.Class` is `Universal` — a port
    * cannot turn that one off and should not be sent to its own manifest to try.
    *
    * Only declarations, and only where the argument mentions a type this phase MAPS: a
    * `Class<String>` is a carrier application that no retyping would have touched, so preserving it
    * decided nothing and a row for it would be noise in every port in the corpus. */
  private def recordReifiedTypeArgs(after: SymbolTable)(using p: Program): Unit =
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

  /** DECISION PROVENANCE for the EGRESS BRIDGE (K21 face 1) — one row per DECLARATION that hands a
    * value to a declared reflective sink.
    *
    * Recorded per declaration and not per site, for §5.1's reason: the call
    * `…Reified.toJavaValue(value)` is already in the diff the reader is holding, and what the diff
    * cannot say is WHICH manifest entry put it there. The key is the sink FQN verbatim, because it
    * is the string an agent edits to turn this off.
    *
    * No porter note (`PorterNote.Rendered` does not carry the kind): the emitted call NAMES the
    * bridge, so the note would restate what the line says — the same reasoning `RedirectedCall`
    * already applies, and the opposite of an invented member. */
  private def recordEgressBridges()(using p: Program): Unit =
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

  /** every (carrier FQN, preserved argument) pair inside a signature whose argument mentions a type
    * this phase MAPS — i.e. every position where the preservation actually decided something.
    *
    * Walked with [[StandardTraversal.mapType]] and not a private recursion, for the reason
    * [[retargetKeysIn]] gives: a hand-rolled walk that stopped at `MethodType`'s parameters would
    * answer "nothing preserved" for every method in the program, silently (§3). */
  private def preservedCarrierArgs(t: TypeRepr)(using Program): List[(String, TypeRepr)] =
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

  /** does this type mention a java type THIS PHASE maps? The question `mentionsRetyped` asks in the
    * other direction — that one reads the types the phase PRODUCED, this one the keys it consumes —
    * and both are §4.56's "conclude only from what the phase itself did". */
  private def mentionsMapped(t: TypeRepr)(using Program): Boolean =
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
    * the same [[coerce]] every other position does.
    *
    * It used to carry ONE answer of its own — a declaration initialised from `map.keySet()` was
    * RETYPED to `scala.collection.Set`, because that is what `m.keySet` emits while the node claimed
    * the retyped `mutable.Set`. That is gone, and its absence is the point: the disagreement is now
    * removed at the REWRITE (see the `keySet` arm), so the value really is a `mutable.Set` and the
    * declaration keeps the type — and the removal capability — java gave it. A position-local answer
    * to a phase-wide disagreement is what left a RETURN unanswered.
    */
  override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
    citeIfReified(t.symbol)
    transformValDefRhs(t)

  private def transformValDefRhs(t: Tree.ValDef)(using Program): Tree.ValDef = t.rhs match
    // a DECLARED slot is an expected type exactly as a formal parameter is — see `coerce`.
    // `Collection<Object[]> parameters = new ArrayList<>()` is the shape, and it is the one that
    // regressed libGDX's test port when only arguments were bridged.
    case Some(rhs) => t.copy(rhs = Some(coerce(t.tpt.tpe, rhs, literal(t.symbol))))
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
    case ty: Tree.Typed   => reifiedCast(ty)
    case io: Tree.InstanceOf => reifiedTest(io)
    case fe: Tree.ForEach => writeThroughEntries(fe)
    case mr: Tree.MethodRef => lowerMethodRef(mr)
    case sel: Tree.Select => staticFieldRewrite(sel).getOrElse(externalFieldProducer(sel))
    case other          => other

  // -------------------------------------------------------------------------------------------
  // REIFIED OCCURRENCES — the retyping moved the TYPE and not the OBJECTS
  //
  // Every other seam this phase owes is a STATIC one: two sides of a slot disagree, and the
  // compiler says so or a boundary finding does. An `instanceof` and a downcast are neither.
  // They ask a question of a RUNTIME OBJECT, java answered it over java's own classes, and after
  // the retyping the emitted `isInstanceOf`/`asInstanceOf` asks it over scala's — a DIFFERENT
  // question, on a program that compiles, with every check count flat. CLAUDE.md §4.4's defect
  // class arriving through a retype rather than through a statement form (`ENGINE-LIMITS.md`
  // K18); measured on liqp at 160 of 183 remaining test failures.
  //
  // The values that can arrive at such a position are of BOTH representations, and that is not a
  // corner case — it is the normal state of a ported library: a `Map<String,Object>` the port's
  // own code built is a `mutable.Map`, and the one jackson deserialised, ANTLR returned or the
  // library's own caller passed in is a `java.util.HashMap`. Java's test accepted every one.
  // `JavaCollections.Reified` is that disjunction, and the coercion that goes with it.
  // -------------------------------------------------------------------------------------------

  /** java's `x instanceof T` where this phase retyped `T`. */
  private def reifiedTest(t: Tree.InstanceOf)(using p: Program): Term =
    reifiedTarget(t.tpt.tpe) match
      case scala.None => unmappedReified("reified type test", t.tpt.tpe, t.origin); t
      case Some(tgt)  => reifiedIsSyms.get(tgt) match
        case Some(f) =>
          reifiedHere = true
          Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(t.expr), f, t.tpe, t.origin)
        case scala.None =>
          reifiedSeam("reified type test", t.tpt.tpe, t.origin)
          t

  /** java's `(T) x` where this phase retyped `T` and cannot VOUCH for what `x` produces.
    *
    * The cast is KEPT and the coercion goes INSIDE it. That is exact rather than tidy: java's own
    * cast to a generic type is unchecked in its type arguments (JLS 5.5), which is precisely what
    * the surviving `asInstanceOf` expresses, while the coercion answers the only part java checked
    * — the erased class. Replacing the cast instead would silently narrow a wildcard-applied
    * target the helper cannot name. */
  private def reifiedCast(t: Tree.Typed)(using p: Program): Term =
    // …asked BEFORE `vouched`, and that is not an oversight. `vouched` says the phase KNOWS the
    // value is one of its own representations, which at a target outside the mapping makes the
    // divergence certain rather than possible: java asked about the `ArrayList` this value used to
    // be. A `null` still has no runtime object to be about, in either direction.
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

  /** the head symbol of a type THIS PHASE produced, or `None` — §4.56's question, asked of the
    * phase's own tables and never of a name. Nothing in java source names a
    * `scala.collection.mutable.*` or a `balticporter.runtime.Java*`, so a reified occurrence at one
    * of these is one this phase put there. */
  private def reifiedTarget(t: TypeRepr): Option[SymId] =
    headSym(t).filter(s => kindOf.contains(s) || shimSyms.contains(s))

  /** Can this phase VOUCH for the REPRESENTATION of the value this expression produces?
    *
    * It can where the value comes out of a declaration it retyped — the port's own code puts a
    * scala collection there, so java's cast was already a no-op on representation and the emitted
    * `asInstanceOf` says the same thing. It cannot where the producer is EXTERNAL, and that is not
    * a hedge: the node's type reads as a mapping target only because `transformType` is
    * position-blind and moved it, while the value is whatever the class file makes —
    * `mapper.readValue(json, HashMap.class)` is a `java.util.HashMap` under a node claiming
    * `mutable.HashMap` (K15's own observation, met at a cast instead of at a slot).
    *
    * ==and a type the PROGRAM DECLARES is vouched for by ownership, not by the mapping==
    * `(Iterator<T>) new QueueIterator<T>(…)` is a cast of a class this port EMITS to a shim that
    * class already implements. The representation is not in question — every instance of a
    * program-declared type is one the port made — so a coercion there is an identity call the
    * emitted code pays for on a hot path (libGDX's `Queue.iterator()` and `Array.select`, 9
    * members). `Program.owns` is the structural test §4.56 asks for, and it is a DIFFERENT reason
    * from the mapping one: the first says "this phase put a scala collection here", the second says
    * "this program declares what this is". Both are the phase reasoning from what it can see. */
  private def vouched(e: Term)(using p: Program): Boolean =
    (reifiedTarget(e.tpe).isDefined || headSym(e.tpe).exists(p.owns)) && !foreignProducer(e)

  /** …and the one exception to it: a call or field read the PROGRAM DOES NOT DECLARE.
    * [[externalCallee]] is K15's predicate unchanged, exclusions included. */
  private def foreignProducer(e: Term)(using p: Program): Boolean = e match
    case a: Tree.Apply  => externalCallee(a.method)
    case s: Tree.Select => externalCallee(s.sym)
    case _              => false

  /** `null` is an instance of nothing and a cast of it checks nothing, in either language — so
    * there is no runtime object for a reified question to be about. Left exactly as it was, which
    * also keeps `TirEmitter`'s `null.asInstanceOf[T]` shapes (an uninitialised field, a funnel
    * slot) recognisable to the passes that read them. */
  private def isNullLiteral(e: Term): Boolean = e match
    case Tree.Literal(Constant.NullC, _, _) => true
    case _                                  => false

  /** a reified occurrence at a target no live view can BE — `mutable.HashMap`, `ArrayBuffer`,
    * `Tuple2`. Refused and counted rather than approximated (M6); the emitted code keeps java's own
    * question asked of the wrong classes, which is what the finding says. */
  /** …and drain [[reifiedHere]] at the declaration the rewrite happened in. */
  private def citeIfReified(sym: SymId)(using p: Program): Unit =
    if reifiedHere then
      cite(balticporter.catalog.JS.G(48), p.symbolOf(sym).map(_.fullName).getOrElse(sym.toString))
      reifiedHere = false

  private def reifiedSeam(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    seam(slot, "a representation-agnostic test or coercion",
         TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
         CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** …and the reified occurrence at a target this phase did NOT retype, which is the one shape with
    * no instrument on it at all.
    *
    * `x instanceof java.util.RandomAccess` names nothing this phase moved, so every arm above
    * declines and the node is emitted verbatim — valid Scala, asking a question that answers NO for
    * every value the phase retyped, where java answered YES for the `ArrayList` that value used to
    * be. `SortedMap`, `Cloneable`, `Serializable`, `SequencedCollection` are the same site at other
    * names. Unlike a CONCRETE mapping target there is not even a helper the engine could write:
    * `mutable.Buffer` is not a `RandomAccess` and no live view can make it one, because the target
    * is outside the family the mapping is a mapping OF.
    *
    * So it is REFUSED and COUNTED (M6), never approximated — and the count is the whole of what
    * exists here: no compile error, no coercion, and no member digest moves.
    *
    * ==Decided from the phase's own table, never from a name (§4.56)==
    * [[CollectionsTransform.unmappedSupertypes]] is the SUPERTYPE CLOSURE of `typeMap`'s own java
    * keys minus those keys — a fact the phase can derive because it is the phase that chose the
    * keys. A name test would be exactly the failure §4.56 records: libGDX's
    * `com.badlogic.gdx.utils.Json$Serializable` is a `Serializable` by simple name and shares
    * nothing at all with `java.io.Serializable`. */
  private def unmappedReified(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    if headSym(target).exists(unmappedSupertypeSyms) then
      seam(slot, "no coercion exists: the target is OUTSIDE the mapping, and a retyped value is not one",
           TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
           CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** A JDK STATIC FIELD that is java's own RAW alias for a factory this phase already rewrites —
    * `Collections.EMPTY_LIST`, `EMPTY_SET`, `EMPTY_MAP`.
    *
    * These three are declared RAW (`public static final List EMPTY_LIST`), which is not an accident
    * of age but the reason java's own javadoc points readers at `emptyList()` instead: reading one at
    * a parameterised slot is an UNCHECKED CONVERSION (JLS 5.1.9), legal with a warning, and flexmark
    * writes `@SuppressWarnings("unchecked")` over both of its sites. Scala has no unchecked
    * conversion, so [[externalFieldProducer]]'s otherwise-correct wrap produced
    * `Buffer[java.util.Collections.EMPTY_LIST.E]` — an element type naming the RAW field's own
    * variable, which conforms to nothing.
    *
    * The translation needs no unchecked-conversion machinery, because JAVA ALREADY HAS THE TYPED
    * FORM and says these are it: `EMPTY_LIST` is documented as the field form of `emptyList()`, and
    * `emptyList()` is where the type argument comes from the SLOT rather than from the receiver.
    * So the field rewrites to exactly the helper the CALL already rewrites to, one table over, and
    * the raw type is gone rather than worked around.
    *
    * Reference IDENTITY survives, which is the part a copy would lose: java's `EMPTY_LIST` IS the
    * object `emptyList()` returns, and `JavaCollections.emptyList` hands back one shared instance for
    * exactly that reason (see its own doc) — so `xs == Collections.EMPTY_LIST`, which this engine
    * emits as `eq` (§4.4), keeps answering what java answers.
    *
    * Consulted AHEAD of [[externalFieldProducer]]: both would fire on the same node and the wrap is
    * the weaker answer — it preserves a raw type this one removes. */
  private def staticFieldRewrite(sel: Tree.Select)(using p: Program): Option[Term] =
    for
      m   <- p.symbolOf(sel.sym)
      o   <- p.symbolOf(m.owner)
      nm  <- CollectionsTransform.StaticFieldFactories.get(MemberKey(o.fullName, m.name).render)
      f    = sym(nm)
      if f != SymId.None
    yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, sel.origin), Nil, f, sel.tpe, sel.origin)

  /** A FIELD the program does not declare, whose CLASS FILE types it as a collection this phase
    * retypes — [[externalProducer]]'s fact for the one member kind that has no call node.
    *
    * K15 is about external CALLEES and it is stated for `Tree.Apply`. A field read is the same seam
    * one node kind along and it is invisible to everything keyed on a call: an ANTLR context's
    * `public List<ParseTree> children` really is a `java.util.List`, the position-blind retyping
    * moved the SELECT's node type to `Buffer`, and both the boundary check and the JDK-surface
    * check therefore read a scala collection on both sides — `jdk-surface` reported ZERO on it
    * while scalac read `value foreach is not a member of java.util.List`.
    *
    * ==Why this arm asks the CLASS FILE and not the node==
    * [[externalProducer]] reads the node's `tpe`, because at a CALL there was no readable result to
    * read. Here reading the node is not merely weaker, it is UNSOUND: `mapTerm` visits an
    * `Apply`'s `fun` as a term of its own, so this arm sees every method SELECTION too, and
    * wrapping one would put a `fromJava(...)` where the callee belongs — silently turning every
    * rewritten call in the program into a call on a wrap. The class file separates them exactly:
    * a method's `info` is a `MethodType` (or `NoType` where the file could not be read), and only
    * a FIELD carries a plain type. So the arm fires on "the symbol's declared info is a
    * non-method type whose head is a type `typeMap` covers", which is a fact no method can have.
    *
    * The head is read LITERALLY and never through `remap` — an unowned symbol's signature is a fact
    * about a compiled class file and `StandardTraversal.mapSymbols` deliberately does not move it
    * (§4.56) — and every exclusion [[externalProducer]] states applies unchanged, through
    * [[externalCallee]]. Where the class file cannot be read there is no `info` and this arm does
    * nothing: that is K15's own answer, and the residue is what the count stands for. */
  private def externalFieldProducer(sel: Tree.Select)(using p: Program): Term =
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

  /** the head of a symbol's declared type where that type is a FIELD's — `None` for a method (whose
    * `info` is a `MethodType`), for an unreadable class file (`NoType`), and for anything the
    * mapping does not cover. See [[externalFieldProducer]] for why the method/field distinction has
    * to come from here and cannot come from the node. */
  private def declaredFieldHead(s: SymId)(using p: Program): Option[SymId] =
    p.symbolOf(s).map(_.info).flatMap {
      case _: TypeRepr.MethodType => scala.None
      case TypeRepr.NoType        => scala.None
      case t                      => headSym(t).filter(h => p.symbolOf(h).exists(x => typeMap.contains(x.fullName)))
    }

  /** A METHOD REFERENCE at a member this phase rewrites — `Map.Entry::getKey` inside a stream.
    *
    * The member table already answers `getKey`, and it is keyed on `Tree.Apply`. A method reference
    * is a `Tree.MethodRef`, which the EMITTER expands to `self$ => self$.getKey()` after every
    * phase has run — so the rewrite never saw the call and the emitted lambda selects a member the
    * retyped receiver does not have. Two phases already look at both node shapes
    * (`CallSiteSubstitutionTransform`, `BeanPropertyTransform`); this is one more shape of an
    * existing rewrite, not a new mechanism.
    *
    * **It cannot be a symbol swap.** `getKey` becomes `_1`, which turns an `Apply` into a `Select`,
    * so there is no method left to point the reference at — which is also why teaching the
    * emitter's own expansion the table does not work: it renders `self$.<member>(<args>)` and `_1`
    * is parenless. The phase therefore LOWERS the reference itself, into the lambda the emitter
    * would have built, with the rewritten term as its body.
    *
    * A STATIC reference is `Type.member` and has no receiver to rewrite. The other two forms are
    * both lowered, and the BOUND one (`expr::m`) is the arm `ENGINE-LIMITS.md` K23 named and did not
    * build — its comment then read *"the `Apply` case one node out"*, which is true of a CALL and
    * false of a REFERENCE: `map::get` emits as an eta-expanded `map.get`, a `Tree.Select` that no
    * `Apply`-keyed arm ever sees, so `Map.get`'s `getOrElse(null)` rewrite simply did not happen and
    * the reference handed a `String => Option[V]` to a slot wanting java's `V`.
    *
    * ==the BOUND arm binds its receiver ONCE, which is not tidiness==
    * Java evaluates `expr` at the moment the reference is CREATED and never again (JLS 15.13.3);
    * a lambda `(a0$) => expr.m(a0$)` evaluates it per INVOCATION. For a field read or a call that is
    * a different program — and it is a `CLAUDE.md` §4.4-shaped difference, valid scala meaning
    * something else, with no compile error to report it. So the lowering is
    * `{ val recv$ = expr; (a0$, …) => <rewritten recv$.m(a0$, …)> }`, which is java's own
    * evaluation order written down. `Tree.This` is the one form that skips the binding: it is not a
    * variable, so no assignment can move it, and a `val` for it would be emitted text for nothing.
    *
    * **The parameter is emitted UNANNOTATED, deliberately.** Java writes this qualifier RAW
    * (`Map.Entry::getKey`), so the retyped type renders `Tuple2[?, ?]` and annotating with it makes
    * the body's `_1` an unusable capture — `Set[Any]` where a `Set[String]` was wanted. Scalac
    * infers the parameter from the expected function type, which is exactly what java's own
    * poly-expression rule does, and it is what the emitter's expansion already emits. */
  private def lowerMethodRef(mr: Tree.MethodRef)(using p: Program): Term =
    if selfParamSym == SymId.None then return mr
    // the NODE's answer and not the symbol's, which is the same one derivation the emitter reads
    // (`Tree.MethodRef.referent`, F8): an external member is interned with no `Flags`, so
    // `flags.isStatic` reads `false` for every JDK static and this phase would lower one.
    mr.qualifier match
      case Left(tt) if !mr.referent.isInstanceOf[Referent.Static] =>
        kindOf.get(headSym(tt.tpe).getOrElse(SymId.None)) match
          case None    => mr
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

  /** `Map.Entry.setValue` — WHERE THE MAP IS REACHABLE FROM THE CALL.
    *
    * `entrySet()` maps to the map itself and an entry to a `Tuple2`, which has no write-through;
    * K2 records that as a refusal and the refusal is right, but it was stated at the wrong
    * granularity. The line is not *a `Tuple2` cannot write through* — it is ***`setValue` is
    * unmappable where the MAP IS NOT REACHABLE FROM THE CALL***, and there is exactly one shape
    * where it is:
    *
    * {{{
    * for (Map.Entry<K, V> e : m.entrySet()) { … e.setValue(v); }   // java's ONE legal mutation
    * }}}                                                           // during entry-set iteration
    *
    * The map is not on the entry and it IS ON THE LOOP, so `m.put(e._1, v)` is the same write —
    * and it is the phase's own `Map.put` rewrite, `getOrElse(null)` included, because java's
    * `setValue` returns the PREVIOUS value exactly as `put` does. Emitting `update` instead would
    * discard it, which is the §4.4 shape the `put` arm exists to avoid.
    *
    * Four conditions, and each is a way the rewrite would be wrong without it:
    *
    *   - the loop's SOURCE is a `Kind.Map` — the phase's own record that this receiver is a map it
    *     retyped, never a name test (§4.56). A `Kind.Seq` source's elements are not entries;
    *   - the receiver of `setValue` is the loop's BINDING, not some other entry. `e2.setValue(v)`
    *     inside the loop writes to whatever map `e2` came from, which is not this one;
    *   - the source is a PURE PATH — an `Ident`, or a `Select` chain over `this`/an `Ident`. Java
    *     evaluates the iterable ONCE; re-writing it into the body would evaluate it per iteration,
    *     so a source with any effect (a call, an index) is refused rather than duplicated;
    *   - the binding is not REASSIGNED in the body, or `e._1` is no longer the key the loop is at.
    *
    * What stays refused is the case with no loop and no map — a class holding a detached entry in a
    * FIELD, where the only way to make the body compile is to write to a copy. That is K2's refusal
    * and it keeps it, now with a reason that says which of the two cases it is. */
  private def writeThroughEntries(fe: Tree.ForEach)(using p: Program): Tree.ForEach =
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

  /** the MAP a `for` loop's entry source is a view OF — this phase's OWN `entrySet()` rewrite,
    * whichever shape that rewrite took.
    *
    * The write-through above reads the map off the LOOP, so it has to keep answering when the
    * rewrite's emitted shape changes; asked as `kindAt(iterable) == Kind.Map` alone it silently
    * stopped answering the day `entrySet()` began emitting a live `Set` view instead of the
    * receiver, and a `setValue` inside such a loop would have gone back to writing nowhere with a
    * green compile. So the question is asked of the phase's own record (§4.56): an application of
    * the `entrySetView` symbol THIS RUN minted, or — where the runtime helper is absent and the
    * rewrite therefore handed back the receiver — a source this phase retyped to a `Kind.Map`.
    * Nothing else is an entry source, and a name is never consulted. */
  private def entrySource(src: Term)(using Program): Option[Term] = src match
    case Tree.Apply(_, List(m), f, _, _) if f != SymId.None && f == sym("entrySetView") => Some(m)
    case _ if kindAt(src).contains(Kind.Map)                                            => Some(src)
    case _                                                                              => scala.None

  /** an expression java may evaluate a SECOND time without changing what the program does — an
    * identifier, `this`, or a selection chain over one. Deliberately narrow: the question is asked
    * of a loop source about to be repeated inside the body, and over-approximating it duplicates an
    * effect that no compile error and no check count would report. */
  private def purePath(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This       => true
    case Tree.Select(q, _, _, _)            => purePath(q)
    case _                                  => false

  /** is `s` the target of an assignment anywhere under `body`? `StandardTraversal`'s walk, per
    * CLAUDE.md §3 — a hand-rolled recursion that stopped one node short would answer "no" for the
    * shape this test exists to catch. */
  private def reassigned(s: SymId, body: Term)(using Program): Boolean =
    var hit = false
    val scan = new Phase:
      def name = "binding-reassignment"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          case Tree.Assign(Tree.Ident(`s`, _, _), _, _, _, _) => hit = true; x
          case _                                           => x
    StandardTraversal.mapTerm(scan, body)
    hit

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
    val out = tokenConstructor(t2).orElse(copyConstructor(t2)).orElse(capacityConstructor(t2))
      .orElse(staticRewrite(t2)).getOrElse {
      t2.fun match
        case Tree.Select(recv, m, _, so) => kindAt(recv).orElse(inheritedKind(recv, m)) match
          case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
          // NEITHER answered, so java resolved a member the CLASS declares — and the class may be
          // one this phase gave a scala parent, whose own members are now beside it.
          case None    => pinnedByObject(recv, m, t2).getOrElse(t2)
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
        val bridged = bridgeSinkArgs(bridgeJavaFormals(t2))
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
    if fromJavaSym == SymId.None || !externalCallee(t.method) || instantiation(t) then t
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

  /** Is this application a `new` rather than a CALL?
    *
    * [[externalProducer]] bridges a value an external callee HANDS BACK: the class file says
    * `java.util.List` and the port wants the scala view, so `fromJava` is the faithful wrap. A
    * CONSTRUCTOR hands back nothing of java's — it hands back the object this program just built,
    * which is already whatever this phase retyped its type to. `new java.util.Iterator<E>(){ … }` is
    * a `java.util.Iterator` CONSTRUCTOR reference, so `externalCallee` says yes and the node's `tpe`
    * is the retyped shim, so `liveWrappable` says yes; the port then wrapped an anonymous class that
    * IMPLEMENTS the shim in a converter FROM java, and the error names the helper
    * (`E134 None of the overloaded alternatives of method fromJava`) rather than anything a reader
    * can act on — which is the shape that seam is explicitly built never to produce.
    *
    * Asked STRUCTURALLY and in both spellings the IR admits (§4.56): the applied function is a
    * `Tree.New`, or the resolved method is an initialiser. A test on the node's TYPE could not
    * separate these at all — the constructed value and a returned one have the same type by
    * construction, which is exactly why nothing here was looking. */
  private def instantiation(t: Tree.Apply)(using p: Program): Boolean =
    t.fun.isInstanceOf[Tree.New] || p.symbolOf(t.method).exists(_.name == "<init>")

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
      // OCCURRENCE on BOTH sides, never equality on one of them. The argument half was written as
      // `_.tpe == want` and the receiver half as `occursIn`, and that asymmetry is itself a hole:
      // `Objects.requireNonNull(m)` is the equality case and `mapper.convertValue(v, typeRef)` is
      // the same fact one type argument in — the result `T` is pinned by the TYPE ARGUMENT of an
      // argument (`TypeReference<Map<String,Object>>`), so the value's type comes from what the
      // CALLER handed in and not from a collection the callee built. Wrapping it emitted
      // `fromJava(aScalaMap)`, an E134 naming the HELPER rather than the boundary, which is the
      // worst shape this seam produces. Occurrence subsumes equality, so the first case is unchanged.
      want != TypeRepr.NoType && (
        t.args.exists(a => occursIn(want, a.tpe)) || (t.fun match
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

  /** is this call a read of a WILDCARD CAPTURE that java answered with `Object`?
    *
    * `ENGINE-LIMITS.md` G23's fact at a read: java's unbounded wildcard carries an implicit
    * `java.lang.Object` upper bound (JLS 4.4), so `Object o = it.next()` on a RAW `Iterator`
    * type-checks in java with nothing written anywhere. This phase retypes that receiver onto a
    * `balticporter.runtime` shim whose parameter is UNBOUNDED, so scala's capture is bounded by
    * `Any` and the same read no longer conforms.
    *
    * ==THE EVIDENCE IS STRUCTURAL, AND IT IS A FACT ABOUT THE FOUR TYPES THIS PHASE OWNS==
    * The caller has already established that the receiver is one of `standaloneTargets` and that
    * its SOLE type argument is a wildcard. On such a receiver a recorded result of `Object` can
    * only be java substituting the capture's implicit bound, because **not one of the 76 members
    * the four standalone shims declare returns a bare `Object`** — `toArray` returns an `Array`,
    * `size` an `Int`, `iterator()` another shim. That is §4.56's licensed form of reasoning
    * exactly: a phase concluding something about a type from what the PHASE ITSELF did to it,
    * rather than from a name or a prefix. It is also why the population is small and enumerable —
    * the family has one type parameter, so `wildcardElement` admits no `Map`.
    *
    * ==AND THE `None` POLARITY IS THE OPPOSITE OF ITS NEIGHBOUR'S, DELIBERATELY==
    * [[declaredResultIsMapped]] answers FALSE on an unreadable signature because there the class
    * file would be the evidence and a guess must not stand in for it. Here the class file is a
    * REFUTER, not the evidence: the structural fact above already establishes the capture, and the
    * only thing that could overturn it is a signature that positively says `Object`. Measured —
    * `java.util.Iterator#next()` interns with NO signature at all in this corpus, so an
    * `exists(...)` polarity fires nowhere and the arm is dead code that reads as live. State the
    * refutation, not the confirmation, whenever the confirming artifact may simply be absent. */
  private def capturedObjectRead(t: Tree.Apply)(using p: Program): Boolean =
    def isObject(x: TypeRepr): Boolean =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    isObject(t.tpe) && !declaredResult(t).exists(isObject)

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
    memberKeyOf(m).exists(CollectionsTransform.handledStatics.contains)

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
    * This is the rule the `keySet()` refusal used to carry at a second site — "the recorded type is
    * not a witness of what the emitter will print". That one is GONE, because the `keySet` arm now
    * emits a value that really has the type the node claims; this one stays, because K6.5's aliasing
    * refusal means the emitted text really is a `java.util.List` and no rewrite can change it. The
    * two are the same rule with opposite answers, and the difference is whether the phase can make
    * the emission match the record. It is answered from the phase's own tables rather than
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
    opaqueEgress(t)

  /** THE SEAM WITH NOTHING WRONG WITH IT — `ENGINE-LIMITS.md` K21 face 1, counted.
    *
    * A `java.lang.Object` formal on an external callee takes anything, so a value this phase retyped
    * CONFORMS and the port compiles; what changed is what the callee's `toString`, `instanceof` and
    * serialiser see. There is no type error to find, and the argument's own static type is usually
    * `Object` too — the port's data model holds both representations at every such slot (K18) — so
    * the phase cannot tell from the site whether anything crossed. Only the port knows which of
    * these callees READS the representation, which is what [[reflectiveSinks]] is; a missing entry
    * is otherwise invisible, and this count is the review list that makes it visible.
    *
    * Deduplicated by CALLEE and not by site, because the question is per METHOD — "does this
    * external method read what I hand it?" — and one row per call would bury it under `println`.
    * A declared sink is bridged and does not appear. */
  private def opaqueEgress(t: Tree.Apply)(using p: Program): Unit =
    if !externalCallee(t.method) || sinkOf(t.method).isDefined then return
    val formals = formalsOf(t)
    def objectTyped(x: TypeRepr) =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    val opaque =
      if formals.sizeIs == t.args.size then
        // …the argument is one the phase cannot rule out: a value it RETYPED (which `toJava` covers
        // only one level deep), or one whose static type is `Object` and so says nothing. Anything
        // else — a `String`, a boxed number, a type the phase never touched — is provably not a
        // representation this engine introduced, and counting it would be noise.
        t.args.zip(formals).exists((a, f) => objectTyped(f) && mayBeRetypedValue(a))
      else
        // …and with NO readable signature there is no formal to ask, which is the case a generic
        // external method lands in. Held to an `Object`-TYPED argument on purpose: a
        // collection-typed one at such a callee is already `externalArgs`' row, and one fact on two
        // lanes is what teaches a reader to skim both.
        t.args.exists(a => objectTyped(a.tpe))
    if opaque then
      // …the earliest site PER JAVA FILE, never per callee. The row is still one per callee; which
      // of a callee's files it is reported from is decided at report time, among the paths the
      // module actually emits (see [[boundary]]). Recorded per callee alone, a base's site wins the
      // minimum for the whole program and a dependent's row silently disappears.
      val key  = t.method -> t.origin.javaPath
      val prev = opaqueEgressSites.get(key)
      if prev.forall(o => t.origin.line < o.line) then opaqueEgressSites(key) = t.origin

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
  /** Java's CLASS-TOKEN constructor — `new EnumMap<K, V>(K.class)`.
    *
    * A third constructor shape beside [[copyConstructor]]'s and [[capacityConstructor]]'s, and the
    * only one whose argument the target does not want at all: java needs the token to size its
    * ordinal ARRAY, and the shim orders by `ordinal` instead, so there is nothing to size. It is
    * routed to a named factory rather than having the argument deleted, because a factory reads
    * back as the java it came from and a silently-dropped argument does not.
    *
    * Ordered BEFORE `copyConstructor`, and they are disjoint anyway: this one takes a `classOf[…]`
    * LITERAL, which no `kindOf` covers. */
  private def tokenConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New if enumMapOfTypeSym != SymId.None =>
      val isToken = t.args match
        case List(Tree.Literal(Constant.ClassOfC(_), _, _)) => true
        case _                                              => false
      for tgt <- headSym(n.tpe) if isToken && tgt == byScalaSym(CollectionsTransform.JavaEnumMapFqn)
      yield Tree.Apply(Tree.Ident(enumMapOfTypeSym, TypeRepr.NoType, t.origin), t.args,
                       enumMapOfTypeSym, n.tpe, t.origin)
    case _ => scala.None

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
      // ---- `java.util.EnumSet`, which has NO PUBLIC CONSTRUCTOR — every java site is a static ----
      //
      // So the shim's companion is where the whole type is reached from, and the class token is
      // KEPT rather than dropped: `allOf`, `range` and `complementOf` need the enum's CONSTANTS,
      // which is what `Class.getEnumConstants` is for and what java's own implementation uses.
      // `of` has five fixed arities plus a vararg in java and ONE repeated parameter here — the
      // arities exist only to avoid an array allocation, so a single arm serves them all.
      case (Some("java.util.EnumSet#noneOf"), List(c))       => Some(factory(enumSetSym("noneOf"), List(c)))
      case (Some("java.util.EnumSet#allOf"), List(c))        => Some(factory(enumSetSym("allOf"), List(c)))
      case (Some("java.util.EnumSet#copyOf"), List(c))       => Some(factory(enumSetSym("copyOf"), List(c)))
      case (Some("java.util.EnumSet#range"), List(a, b))     => Some(factory(enumSetSym("range"), List(a, b)))
      case (Some("java.util.EnumSet#complementOf"), List(s)) => Some(factory(enumSetSym("complementOf"), List(s)))
      case (Some("java.util.EnumSet#of"), args)              => Some(factory(enumSetSym("of"), args))
      // ---- the primitive optionals' two factories, which are `Some`/`None` and nothing else ----
      //
      // The target is an ALIAS for `Option[…]`, so these need no runtime member: java's `of(x)` IS
      // `Some(x)` and java's `empty()` IS `None`. `ofNullable` has no arm — `OptionalInt` does not
      // declare one (a primitive cannot be null), and the reference `Optional` this table does not
      // map has it, so there is nothing to be silent about.
      case (Some("java.util.OptionalInt#of" | "java.util.OptionalLong#of" | "java.util.OptionalDouble#of"), List(x)) =>
        Some(factory(someSym, List(x)))
      case (Some("java.util.OptionalInt#empty" | "java.util.OptionalLong#empty" | "java.util.OptionalDouble#empty"), Nil) =>
        Some(Tree.Ident(noneSym, t.tpe, t.origin))
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
        asListArgs(args) match
          // …with JAVA'S OWN INFERENCE re-stated as the explicit type argument. Java infers `T`
          // from all the arguments at once and BOXES what it must:
          // `Arrays.asList(98, "97", true, false, null)` is a `List<Serializable & Comparable<…>>`.
          // Scala infers `A` from the arguments too, and its `Int`/`Boolean` are VALUE types that
          // join to nothing java would name — so at an inferred `A` scalac declines the boxing
          // conversion outright ("implicit conversions were not tried because the result of an
          // implicit conversion must be more specific than T") and reports one mismatch per
          // element. With `A` written down the conversion IS tried, `Predef.int2Integer` applies,
          // and the emitted call is the list java built. See [[elementArg]] for the guard.
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
      // the SHORT-CIRCUITING terminals. `anyMatch`/`allMatch` are scala's `exists`/`forall`
      // exactly — same result, same laziness, same answer on an empty source (`false` / `true`) —
      // so they are plain members and not helpers; a helper here would be indirection with nothing
      // to say. `noneMatch` has no scala namesake and IS a helper, because the alternative is
      // synthesising a negation node for one call: `!xs.exists(p)` is a term this phase has no
      // `unary_!` symbol for, and minting one to save three lines of runtime is the wrong trade.
      case (Some("java.util.stream.Stream#anyMatch"), List(pred)) if existsSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, existsSym, TypeRepr.NoType, t.origin), List(pred),
                                 existsSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#allMatch"), List(pred)) if forallSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, forallSym, TypeRepr.NoType, t.origin), List(pred),
                                 forallSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#noneMatch"), List(pred)) if sym("noneMatch") != SymId.None && collapsed(recv) =>
        recv.map(r => factory(sym("noneMatch"), List(r, pred)))
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
    * ==A single ARRAY argument is the ALIASING form, and it is now a VIEW rather than a refusal==
    * This used to return `None`, refusing the rewrite entirely so the emitted text kept the JDK
    * name and failed to compile as an untranslated call. The refusal was right about the thing it
    * refused — a COPY here silently detaches every aliased write, which is CLAUDE.md §4.4 exactly —
    * and wrong about there being no third answer. `JavaCollections.asListView` is java's own:
    * a fixed-size `Buffer` reading and WRITING THROUGH the array, with `add`/`remove` throwing
    * `UnsupportedOperationException` at the call java throws it at. Nothing about it is an
    * approximation.
    *
    * What blocked it was stated here as "the frontend has already coerced the argument to the
    * ERASED formal (`Array[Object]`), so the element type is gone" — and that is true of the
    * ARGUMENT and not of the tree. The coercion is a `Tree.Typed` the frontend synthesised for the
    * OLD callee's formal, with java's own inference recorded on the CALL: `Arrays.asList(arr)` over
    * an `Insertion[]` has result type `List<Insertion>`. So the element type is recoverable by
    * looking THROUGH a cast this rewrite is about to make irrelevant — which is CLAUDE.md §1(b)'s
    * "a COERCION may not precede a REWRITE of the same call", and `arrayArg`'s rule at a second
    * site. See [[asListViewArg]]. */
  private def asListArgs(args: List[Term])(using p: Program): AsList =
    def isArray(t: TypeRepr) = headSym(t).flatMap(p.symbolOf).exists(_.fullName == "scala.Array")
    args match
      case init :+ Tree.NewArray(_, Nil, Some(elems), _, _) => AsList.Elements(init ++ elems)
      // the EXTERNAL-callee shape of the same pack — opened, never read as one array argument.
      case init :+ Tree.Repeated(elems, _, _)               => AsList.Elements(init ++ elems)
      // …and the THIRD shape of the same java call, which is the aliasing form after K6.5's fourth
      // case: java FORWARDS an array through the `T...` slot, and at an external callee that is a
      // `Tree.Spread` (`arr*`), not a pack. It is one argument that IS the caller's array, so it is
      // this arm and not the two above — and the spread comes OFF, because `asListView` takes the
      // array itself. Left on, the emitted `asListView(arr*)` is `Sequence argument type annotation
      // '*' cannot be used here`, i.e. the rewrite firing at the right site with the wrong shape.
      case List(Tree.Spread(e, _, _)) if isArray(e.tpe)     => AsList.Aliased(e)
      case List(a) if isArray(a.tpe)                        => AsList.Aliased(a)
      case _                                                => AsList.Elements(args)

  /** the element type a `TypeTree` may be written for — java's own inference, made explicit.
    *
    * Yielded only when the call's result really names one type: a `TypeBounds` is a wildcard and
    * writing `?` in a TERM position is not syntax (K10's rule), an unresolved inference marker
    * names nothing (G2), and `NoType` is the frontend saying it does not know. In every one of
    * those cases the call is left to scala's own inference, which is what it did before — so the
    * guard is what keeps this from being a regression at the shapes it cannot help. */
  private def elementArg(t: Tree.Apply)(using p: Program): Option[TypeTree] =
    soleTypeArg(t.tpe).collect {
      case a if a != TypeRepr.NoType && !a.isInstanceOf[TypeRepr.TypeBounds] && !namesUnresolved(a) =>
        TypeTree(a, t.origin)
    }

  /** does this type mention an inference MARKER the frontend interned for a diamond's inferred
    * argument, or a WILDCARD, anywhere inside it?
    *
    * Printed, `?E` names nothing and does not lex (G2), and a `?` in a TERM position is not syntax
    * at all (K10) — so a type carrying either may not be written down as an explicit type argument.
    * Read through `Symbol.isUnresolvedTypeVar`, which is where `api` owns the prefix, never a local
    * spelling of it. */
  private def namesUnresolved(t: TypeRepr)(using p: Program): Boolean = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).exists(x => Symbol.isUnresolvedTypeVar(x.fullName))
    case TypeRepr.AppliedType(c, as) => namesUnresolved(c) || as.exists(namesUnresolved)
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AndType(l, r)      => namesUnresolved(l) || namesUnresolved(r)
    case TypeRepr.OrType(l, r)       => namesUnresolved(l) || namesUnresolved(r)
    case _                           => false

  /** the argument `asListView` should receive — [[arrayArg]]'s rule at `Arrays.asList(T[])`.
    *
    * Java's `asList` is declared `<T> List<T> asList(T... a)`, whose ERASED formal is `Object[]`, so
    * the frontend synthesises `arr.asInstanceOf[Array[Object]]` off the declared formal (G14, the
    * same rule that widens a map key to `Object`). `asListView[A]` infers `A` FROM the argument, so
    * with the cast left on it infers `Object` and hands back a `Buffer[Object]` where java's call —
    * which inferred `T = Insertion` from the unerased argument — produced a `List<Insertion>`.
    *
    * The test is STRUCTURAL and names no type (CLAUDE.md §4.56): strip when the cast wraps an array
    * whose ELEMENT type is the one this call RESULTS in — that is precisely "java inferred `T` from
    * the unerased argument", since the call's recorded result type is `List<T>` and this phase has
    * already retyped it to `Buffer[T]`. A java source that really wrote `(Object[]) value` inferred
    * `T = Object`, so both elements are `Object`, the strip is a no-op on the type, and the cast the
    * JAVA wrote survives underneath it. */
  private def asListViewArg(arg: Term, call: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) =>
      val wanted = soleTypeArg(call.tpe)
      val have   = soleTypeArg(inner.tpe)
      if wanted.isDefined && wanted == have then inner else arg
    case _ => arg

  /** the single type argument of an applied type, or `None` — the one shape [[asListViewArg]]
    * compares. Deliberately not a general "element type of": a `Buffer[A]` and an `Array[A]` both
    * have exactly one, and anything else is not a pair this strip may reason about. */
  private def soleTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(a)) if a != TypeRepr.NoType => Some(a)
    case _                                                        => scala.None

  /** which of `Arrays.asList`'s two java shapes a call site is — see [[asListArgs]]. An `Option`
    * could not say it: the aliasing form is not "no arguments to pass", it is a DIFFERENT helper,
    * and reading the absence as a refusal is what kept the view out of reach for two waves. */
  private enum AsList:
    case Elements(args: List[Term])
    case Aliased(array: Term)

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
      val formals = instantiatedFormals(t, formalsOf(t))
      if formals.sizeIs != t.args.size then t
      else
        val as = t.args.zip(formals).map((a, f) => coerce(f, a))
        if as == t.args then t else t.copy(args = as)

  /** …the formals this call's OWN ARGUMENTS INSTANTIATE — the slot with NO HEAD to coerce against.
    *
    * `ENGINE-LIMITS.md` K26's first blindness, met at the FIX rather than at the count.
    * `set(DataKey<T> key, T value)` called as `set(EXTENSIONS, list)` binds ONE type variable to
    * both sides of a java subtyping edge this mapping has no image for: the KEY says
    * `T = Collection<E>`, which is the standalone shim (§4.5 says it must be), and the VALUE is an
    * `ArrayList<E>`, which is a `scala.collection.*`. Java compiled it because `ArrayList <:
    * Collection`; the port does not. And [[coerce]] cannot repair it as written, because the formal
    * at the value slot is a BARE type variable and every arm of the factory table asks for a HEAD —
    * which is exactly why `collection-boundary` reads zero here and why the internal lane had to
    * exist.
    *
    * ==Java's own resolution is what supplies the head, and its ASYMMETRY is the whole rule==
    * `DataKey<T>` is INVARIANT, so the key argument fixes `T` exactly (JLS 18.2.1) and the value is
    * then converted TO it; the bare occurrence only bounds `T` from below and decides nothing.
    * Reproduce that and nothing else: a PARAMETERISED formal binds, a bare one is the slot being
    * answered. Read the other way round the substitution would say `T = ArrayBuffer[E]` and defeat
    * its own purpose.
    *
    * ==WHICH variables this call may bind is OWNERSHIP, never a name==
    * §4.56, whose hazard is at its sharpest here: a class's `<V>` and a method's `<V>` are one
    * string. The frontend mints a method's type parameter with `owner = <that method>`, so the test
    * is a symbol comparison — and a CLASS's parameter, which the RECEIVER fixes and which this call
    * cannot bind, is skipped. `CollectionInternalCheck.typeVariableSplit` reads the same fact the
    * same way and by the same test, which is what keeps the lane that COUNTS this residue and the
    * pass that DRAINS it from disagreeing about which slot is which. The class-parameter shape is
    * therefore still a counted refusal rather than a wrap: closing it needs the receiver's
    * instantiation, which is a different derivation.
    *
    * Nothing here decides to wrap. The substituted formal goes to [[coerce]] exactly as a written
    * one does, so every guard, every refusal and every absent factory still answers — and a call
    * whose variable nothing binds comes back with the formals it arrived with. */
  private def instantiatedFormals(t: Tree.Apply, formals: List[TypeRepr])(using p: Program): List[TypeRepr] =
    if formals.sizeIs != t.args.size then formals
    else
      val bound = collection.mutable.HashMap.empty[SymId, TypeRepr]
      // …AND AT A CONSTRUCTOR THE CLASS'S OWN PARAMETERS ARE BOUND TOO, which is the "different
      // derivation" the paragraph above names and defers. It defers it because a class parameter is
      // fixed by the RECEIVER, and an ordinary call's receiver is an arbitrary term this phase would
      // have to re-derive an instantiation for. A `new` has no receiver: the instantiation IS the
      // node's own type, `DataKey[JavaCollection[X]]`, which the frontend recorded from java's own
      // inference of the diamond. So the binding is READ rather than reconstructed, and it is exact.
      //
      // Without it a `new C<Collection<X>>(…, aList)` is the identical blindness one declaration
      // over: the formal is the bare `T`, no arm of the factory table has a head to compare, and the
      // seam java's `List <: Collection` edge carried reaches scalac as `E134 None of the overloaded
      // alternatives`. The value slot is the one being ANSWERED here exactly as it is above — a
      // class parameter never binds FROM an argument — so the asymmetry that makes the method case
      // sound is preserved verbatim.
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
      // …the recursion is through MATCHING HEADS only. An `AppliedType` whose heads differ is a slot
      // the ordinary boundary lane already reports, and unifying across it would invent a binding
      // java never made. FIRST WINS: a variable a well-typed java call bound twice at two
      // parameterised formals bound it to one type, and where it did not, this phase has no standing
      // to pick — the residue stays the counted one.
      def bind(f: TypeRepr, a: TypeRepr): Unit = (f, a) match
        case (TypeRepr.TypeRef(_, s), _) if bindable(s) && a != TypeRepr.NoType =>
          if !bound.contains(s) then bound(s) = a
        case (TypeRepr.AppliedType(ftc, fs), TypeRepr.AppliedType(atc, as))
          if headSym(ftc) == headSym(atc) && headSym(ftc).isDefined && fs.sizeIs == as.size =>
          fs.lazyZip(as).foreach(bind)
        case _ => ()
      formals.lazyZip(t.args).foreach {
        // a formal that IS a bare reference is the slot being answered, never the binder — see the
        // asymmetry above. (A non-variable `TypeRef` binds nothing either way, so one arm serves.)
        case (_: TypeRepr.TypeRef, _) => ()
        case (f, a)                   => bind(f, a.tpe)
      }
      if bound.isEmpty && ctorBound.isEmpty then formals
      else formals.map {
        case f @ TypeRepr.TypeRef(_, s) if bindable(s)    => bound.getOrElse(s, f)
        case f @ TypeRepr.TypeRef(_, s)                   => ctorBound.getOrElse(s, f)
        case other                                        => other
      }

  /** the TYPE PARAMETERS a class declares, in declaration order, or `Nil` for one this program does
    * not declare — whose parameters are a fact about a class file (§4.56) and are not bindable here
    * either way. Read off the `ClassDef`, which is where the IR keeps them. */
  private def classTparams(owner: SymId)(using p: Program): List[SymId] =
    classDefsBySym.get(owner).map(_.tparams.map(_.symbol)).getOrElse(Nil)

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
    literal(t.method) || externalCallee(t.method) || (t.fun match
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
        // …and the one external callee whose OPAQUE slot is not opaque to IT: a declared reflective
        // sink reads the runtime representation of what it is handed (K21 face 1). Asked here and
        // not in `coerce`, because the sink is a fact about the CALLEE and `coerce` is per argument.
        val sink = if external then sinkOf(t.method) else scala.None
        val as = t.args.zip(formals).map((a, f) =>
          coerce(f, a, expectedScoped = true, expectedExternal = external,
                 expectedSink = sink.isDefined))
        if as != t.args then sink.foreach(fqn => bridgedSinkCallees += (t.method -> fqn))
        if as == t.args then t else t.copy(args = as)

  /** …and the SAME BRIDGE where there is no formal to read.
    *
    * A generic external method has no readable `MethodType` here — measured on the very call K20
    * closed, whose seam this file already files as "no signature" — so the arity test above
    * declines and the argument-side bridge never runs. For an ordinary external callee that
    * refusal is the honest answer and is counted; for a DECLARED SINK it is not, because the port
    * has already stated the fact the signature would have carried. The formal cannot say which
    * slot is opaque, so the ARGUMENT does: a value this phase retyped, or one typed
    * `java.lang.Object`, is a value it cannot prove it did not put there. Everything else — a
    * class token, a super-type token, a `String` — is left exactly as it was, which is what keeps
    * `convertValue(v, Map.class)` a two-argument call with one bridged argument.
    *
    * Measured: with the arity path alone, ONE of liqp's seven sink sites bridged. */
  private def bridgeSinkArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
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

  /** could this value be a representation THIS PHASE introduced? The three answers it cannot rule
    * out: a type it retyped, one of its own shims, and `java.lang.Object`, which says nothing.
    * Read from the phase's own tables, never from a name (§4.56). */
  private def mayBeRetypedValue(a: Term)(using p: Program): Boolean =
    headSym(a.tpe).exists(s => kindOf.contains(s) || shimSyms.contains(s) ||
      p.symbolOf(s).exists(_.fullName == CollectionsTransform.ObjectFqn))

  /** `java.lang.Object` as this program spells it — the bridge's result type. Falls back to the
    * argument's own type where the program never names `Object`, which cannot happen for a value
    * this bridge accepts and is still not worth a crash. */
  private def objectTpe(a: Term)(using p: Program): TypeRepr =
    p.symbols.all.find(_.fullName == CollectionsTransform.ObjectFqn)
      .map(s => TypeRepr.TypeRef(TypeRepr.NoPrefix, s.id)).getOrElse(a.tpe)

  /** the declared REFLECTIVE SINK this callee belongs to, by its OWNER — the phase's own policy
    * read as symbols, never as a name test on the callee (§4.56). `None` where the port declares
    * none, which is every port that has not met one. */
  private def sinkOf(m: SymId)(using p: Program): Option[String] =
    if sinkSyms.isEmpty then scala.None
    else p.symbolOf(m).map(_.owner).filter(sinkSyms.contains).flatMap(p.symbolOf).map(_.fullName)

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
    citeIfReified(t.symbol)
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
    * | `Kind.Stack` (`JavaStack`) | `JavaIterable.from` | `JavaCollection.from` |
    * | `Kind.Set` (`mutable.Set` & co) | `JavaIterable.from` | `JavaCollection.fromSet` |
    * | `Kind.Map` (`mutable.Map` & co) | `JavaIterable.from` | REFUSED — see below |
    * | `Kind.Entry` (`Tuple2`) | n/a | n/a |
    *
    * `Kind.Stack` shares `Kind.Seq`'s row rather than having one of its own, and the reason is
    * worth stating because the kind's whole existence argues the other way: `JavaStack` extends
    * `mutable.ArrayBuffer`, so as a VALUE at a slot it simply IS a `Kind.Seq` and the subtyping
    * licence is identical. The kind is a fact about the CALL REWRITE table — `peek()` means the
    * opposite end from the `Deque` `peek` and one arm cannot answer both — and nothing about that
    * reaches a boundary. Absent from these arms it matched no factory at all, and the boundary
    * check then reported a seam that reads exactly like the honest refusals beside it.
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
    * `map.keySet()` used to be refused here whatever the target, because its node claimed the
    * retyped `mutable.Set` while the scala it emitted was `m.keySet` — a `scala.collection.Set`, and
    * ENGINE-LIMITS §0's "the recorded type is not a witness of what the emitter will print". That
    * refusal is gone, and not by relaxing it: the `keySet` arm now emits a LIVE `mutable.Set` view,
    * so the record and the emission agree and every arm above serves it like any other `Kind.Set`.
    * `Map.values()` and `entrySet()` reached the same invariant the same way. What is still refused
    * on those grounds is [[refusedRewriteSource]], where the emitted text is a JDK call this phase
    * deliberately did not move. */
  private def coerce(expected: TypeRepr, actual: Term, expectedScoped: Boolean = false,
                     expectedExternal: Boolean = false, expectedSink: Boolean = false)(using p: Program): Term =
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
    // …AND A CONDITIONAL'S CONVERSION BELONGS TO ITS BRANCHES, which is java's own rule and not a
    // convenience. JLS 15.25 assigns each operand of a reference conditional to the target type
    // SEPARATELY, so java converted the two arms independently; this phase saw one `Tree.If` whose
    // own type is already the LUB of two arms it has moved, matched no factory against it, and left
    // both. The frontend's `coerce` learned this at an unchecked conversion and states the same
    // reason; here it is the arm that produces a value the retyping has to bridge — a `Map.values`
    // view in one branch and a `Collections.emptyList()` in the other, where the lub is neither.
    //
    // Recursing through THIS function and not around it, so a branch gets whatever wrap IT needs,
    // every guard and every refusal still answers, and a nested conditional resolves one level down.
    // Identity-preserving where nothing moved, so no digest shifts for a conditional this phase has
    // no opinion about.
    actual match
      case i: Tree.If =>
        val th = coerce(expected, i.thenp, expectedScoped, expectedExternal, expectedSink)
        val el = coerce(expected, i.elsep, expectedScoped, expectedExternal, expectedSink)
        return if (th ne i.thenp) || (el ne i.elsep) then i.copy(thenp = th, elsep = el) else i
      case _ => ()
    val (actualT, actualScoped) = actualOf(actual)
    val wants = headSym(expected).map(scalaSym(_, expectedScoped))
    val got   = headSym(actualT).map(scalaSym(_, actualScoped))
    // …and where the value is a type the PROGRAM declares, `kindOf` says nothing: it is keyed on
    // this phase's own scala TARGETS. The class's minted ANCESTRY is the same record read one hop up
    // ([[mintedSourceKind]]) and is what closes `ENGINE-LIMITS.md` K26's `DeclaredSubtype` half.
    val from  = got.filterNot(shimSyms.contains)
                   .flatMap(g => kindOf.get(g).orElse(mintedSourceKind(g, wants)))
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
    // …and the slot THIS PHASE'S OWN COLLAPSE creates. `xs.stream().map(f)` becomes `xs.map(f)`,
    // which is right wherever the chain's terminal is inside the program; where it is not, the
    // value crosses back out to java at a `Stream` formal and no `toJava` overload serves it
    // (`Found: Buffer[LNode] / Required: Stream[? <: LNode]`). `toStream` is the faithful answer
    // and not a compromise, for the same reason `toJava` is at the universal slot: java's value at
    // that slot really WAS a `Stream`. EXTERNAL only — a `Stream` formal on a declaration this port
    // emits is a formal the port itself decided, and the collapse would have moved it too.
    val wantsStream = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.StreamFqn)
    // …asked so that an ABSENT shim can never match. A shim symbol is `SymId.None` when nothing in
    // the program maps to it (`javaIterableSym` exists only where something names
    // `java.lang.Iterable`), and a `wants` of `Some(SymId.None)` — an expected type whose head did
    // not resolve — would then satisfy `contains` and wrap a value in a factory for a type this run
    // does not have. That is the real hazard the pass-level `javaIterableSym` gate was standing in
    // front of, and it belongs here, per target, where it costs no OTHER target its bridge.
    def wantsIs(s: SymId) = s != SymId.None && wants.contains(s)
    val factory = from match
      case _ if wants.isEmpty || refusedRewriteSource(actual) => SymId.None
      // …THE EGRESS BRIDGE (K21 face 1), ahead of every arm below because at a declared reflective
      // sink it SUBSUMES them. `toJava` is one level and is refused outright where the element type
      // is retyped; a sink walks the whole tree, so the one-level answer is wrong there in exactly
      // the case the refusal already names. Fired on the FORMAL and not on `from`: the argument's
      // own type is usually `java.lang.Object`, which is why nothing static could see this seam —
      // the helper is identity for every value this engine did not put there.
      case _ if expectedSink && wantsUniversal && toJavaValueSym != SymId.None => toJavaValueSym
      // `Kind.Stack` rides with `Kind.Seq` in every arm below, and the licence is the SAME one and
      // not a resemblance: `JavaStack` extends `mutable.ArrayBuffer`, so at a slot it IS a
      // `Kind.Seq` value — `JavaIterable.from` takes a `scala.collection.Iterable` and
      // `JavaCollection.from` a `scala.collection.Seq`, and it conforms to both. The kind exists
      // for the CALL REWRITE table, where `peek()` means the opposite end from the `Deque` `peek`
      // and one arm cannot be both (see [[Kind.Stack]]); nothing about that reaches a boundary.
      // Left out, a stack at a bridged slot matched no factory and the boundary check reported it
      // as a refusal indistinguishable from the honest ones beside it — `ENGINE-LIMITS.md` K2.5.
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map) if wantsIs(javaIterableSym) => iterableFromSym
      case Some(Kind.Seq | Kind.Stack)          if wantsIs(javaCollectionSym)  => collectionFromSym
      case Some(Kind.Set)                       if wantsIs(javaCollectionSym)  => collectionFromSetSym
      // `asJava` converts ONE level, exactly as `asScala` does, so a `Buffer[Buffer[String]]` at a
      // `java.util.List<java.util.List<String>>` formal would emit a wrap that lies one type
      // argument in. Refused and counted, the same way [[externalProducer]] refuses the mirror.
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal || wantsStream) && mentionsRetyped(actualT)     => SymId.None
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal) && toJavaSym != SymId.None                     => toJavaSym
      // …and a `Stream` FORMAL takes the collapse's result back to java. `Kind.Map` is excluded:
      // java's `Map` has no `stream()`, so no valid java sends one to such a slot — the same
      // asymmetry the `JavaCollection` row above records.
      case Some(Kind.Seq | Kind.Stack | Kind.Set) if wantsStream && toStreamSym != SymId.None => toStreamSym
      // …and the RETAINED PARENT's own slot (K5.7's other half). The value is a class this program
      // declares that kept java's `Map.Entry`, and the slot is the `Tuple2` the mapping gave every
      // USE of that interface. `from` is `None` here by construction — `declaredParentKinds` drops
      // an uninheritable target — so this arm cannot shadow one above it. The capability is decided
      // in [[detachedEntriesIn]], off the LIBRARY's own body, never here.
      case _ if entryToPairSym != SymId.None &&
                got.flatMap(detachedEntries.get).exists(tgt =>
                  wants.flatMap(p.symbolOf).exists(_.fullName == tgt))            => entryToPairSym
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

  /** the runtime shims, as scala symbols — a source already typed as one is never re-wrapped. */
  private def shimSyms: Set[SymId] =
    Set(javaIterableSym, javaIteratorSym, javaListIteratorSym, javaCollectionSym)

  /** the shims as FQNs, so a `typeMap` TARGET can be recognised as one.
    *
    * [[shimSyms]] answers only for a program that NAMES the shim's java original — the symbols are
    * interned on first reference — while a subtype question has to be answerable about a parent this
    * run resolved by any route. */
  private def shimFqns: Set[String] = CollectionsTransform.ShimFqns

  /** Does a value of this type END UP shim-shaped — the shim itself, or a type THIS PROGRAM
    * DECLARES that inherits from one?
    *
    * The blanket refusal below was asked of the receiver's HEAD SYMBOL against three shim symbols,
    * which is exact for a receiver whose declared type this phase retyped and answers `false` for
    * the one shape a library that defines its own iterator is made of: `interface Cursor<E> extends
    * java.util.Iterator<E>` is retyped to `trait Cursor[E] extends JavaIterator[E]`, so the emitted
    * receiver carries JAVA's arity — while `headSym` is `Cursor`, no shim, and
    * [[inheritedKind]] correctly reports `Kind.Iterator` because `hasNext` really does resolve to
    * `java.util.Iterator#hasNext`. The two together strip the `()` from a call to a member declared
    * `def hasNext()`, at every such receiver in the program.
    *
    * That is `CLAUDE.md` §4.56's guard rule twice over: a test written against the three symbols the
    * phase MINTS keeps answering for those three and silently answers for every SUBTYPE added by a
    * library since, and the fact it is really about — *this receiver's members have java's arity and
    * java's names* — is inherited, so it has to be asked of the ancestry.
    *
    * Both spellings of a parent are accepted because both are reachable: a parent this pass has
    * already retyped names the shim symbol, and one it has not yet reached still names the java
    * original, whose `typeMap` TARGET is the shim. Deciding from either is a fact about what the
    * PHASE ITSELF did to that type, never about its name.
    *
    * Fuel-bounded, and a chain that exhausts the fuel answers `false` — the pre-guard behaviour,
    * which is the conservative arm here because the guard only ever SUPPRESSES a rewrite. */
  private def shimShaped(t: TypeRepr)(using p: Program): Boolean =
    def isShim(s: SymId): Boolean =
      shimSyms.contains(s) ||
        p.symbolOf(s).map(_.fullName).exists(fq => typeMap.get(fq).exists((tgt, _) => shimFqns(tgt)))
    // WHAT SITS ABOVE THIS SYMBOL — a class's parents, and a TYPE PARAMETER's upper BOUND. The
    // second is not an extra case, it is the same question at the other kind of declaration: a
    // receiver typed `I` where `I extends Cursor<Integer>` denotes a value whose members are
    // `Cursor`'s, so java's arity reaches it exactly as it reaches a subclass. Read off the bound
    // and never off `Object`: an unbounded parameter has nothing shim-shaped above it.
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
      * default and an unsafe one cannot be added by omission.
      *
      * …and it is asked of the ANCESTRY, not of the head symbol — see [[shimShaped]]. A library's
      * own `Cursor extends java.util.Iterator` is emitted `extends JavaIterator`, so its members
      * carry java's arity while its head symbol is no shim at all: 16 measured `must be called with
      * () argument` errors on one port, every one of them a receiver typed at a program-declared
      * subtype. */
    val onShim = shimShaped(recv.tpe)
    /** is the receiver `super`? Scala admits `super` in exactly ONE position — as the qualifier of
      * a member selection — and three of the shapes below put it somewhere else: `entrySet` returns
      * the receiver ALONE (`for (e <- super)`), the `Seq` `get` makes it a function (`super(i)`),
      * and every `+=`/`-=`/`++=` used to render INFIX (`super ++= m`). All three are E040 SYNTAX
      * errors, and a syntax error is strictly worse than the type error it replaces: it cannot be
      * attributed to a member and it can take the rest of the file with it.
      *
      * This was a BLANKET refusal, on the grounds that "which of these renders infix" is a fact
      * about the EMITTER this phase cannot read. Two things changed that. The emitter now renders
      * an operator on a `super` receiver as an ordinary selection (`super.++=(m)`), which is legal
      * and is the only legal spelling — so the infix face is gone at its source rather than avoided
      * here. And the remaining question is not "which arm" but a STRUCTURAL property of the RESULT
      * that this phase can simply check: does every `Tree.Super` in what I built stand as the
      * qualifier of a `Tree.Select`? See [[superPlaced]] — asked of the rewrite AFTER it is built,
      * so a new arm is covered by construction and cannot reintroduce the syntax error by omission,
      * which is exactly the property the blanket refusal was bought for. */
    val onSuper = recv.isInstanceOf[Tree.Super]
    val out = (name, t.args, k) match
      // The one exception, and the reason it is one: java 8's `forEach(Consumer)` has no
      // counterpart on the shim itself — `JavaIterable` supplies `foreach` as an EXTENSION, which
      // is the whole point of the family (§4.5: an extension adds a view and cannot conflict).
      // Left alone, this is a call to a member that does not exist.
      case ("forEach", List(f), _) => Some(call(recv, foreachSym, List(f), t, so))
      // …and the SECOND exception, which is one for the OPPOSITE reason: it does not reshape the
      // call at all. The guard below is exactly right that no SCALA-SHAPED rewrite may touch a
      // shim — and what has to happen at `toArray` is not a rewrite of the call but the removal of
      // a COERCION built for the callee this phase has already replaced.
      //
      // Java declares `<T> T[] toArray(T[] a)` and its erased formal is `Object[]`, so the frontend
      // synthesises `EMPTY.asInstanceOf[Array[Object]]` (G14, correct against a CLASS FILE). The
      // shim's `toArray[A](Array[A]): Array[A]` infers `A` FROM the argument exactly as java's own
      // inference did — so with the cast left on it infers `Object` and hands back an
      // `Array[Object]` where java's call produced the element type it read off the UNERASED
      // argument. On this port that `Array[Object]` then matched none of the three `addHandlers`
      // overloads java resolved among.
      //
      // `arrayArg` IS that derivation and is not re-stated here (F8): it strips the cast exactly
      // where what lies beneath already has the call's own recorded result type, which is precisely
      // "java inferred `T` from the unerased argument". This arm is a SECOND CALLER of it and
      // nothing more — the call keeps java's name, its arity and its receiver, so the blanket
      // guard's rule is not weakened by it. `CLAUDE.md` §1's *a COERCION may not precede a REWRITE
      // of the same call*, met where the rewrite in question is the RETYPING of the receiver rather
      // than a new callee.
      case ("toArray", List(a), _) if onShim =>
        val stripped = arrayArg(a, t)
        Option.when(stripped ne a)(t.copy(args = List(stripped)))
      // …and the THIRD exception, which is G23's own fact met at a READ instead of at `addAll`,
      // and which is an exception for `toArray`'s reason rather than `forEach`'s: it reshapes no
      // call. The name, the arity and the receiver are java's; what is added is the COERCION the
      // retyping of the receiver made necessary.
      //
      // `java.util.Iterator` RAW means `Iterator<? extends Object>` — java's unbounded wildcard
      // carries an implicit `Object` upper bound (JLS 4.4), so `Object o = it.next()` type-checks
      // in java with no cast anywhere in the source. This phase retypes that receiver to
      // `JavaIterator[?]`, whose parameter is UNBOUNDED, so scala's capture is bounded by `Any` —
      // strictly wider — and the same read reads `Found: it.A / Required: Object`.
      //
      // ==WHY THE COERCION AND NOT THE BOUND==
      // Bounding the shims' parameters (`trait JavaIterator[A <: AnyRef]`) is the obvious fix and
      // is `ENGINE-LIMITS.md` G24's measured minefield, entered from the other end: in Scala 3
      // `java.io.Serializable` is rooted at `Any`, so `Serializable </: java.lang.Object` and the
      // bound would reject a type java admits. And widening what `?` RENDERS as is G2's settled
      // design space, re-stated by G23 — "do not fix this by changing what `?` renders as". So the
      // difference is stated at the ONE operation it blocks, exactly as G23 states it at `addAll`,
      // and the coercion is java's own erasure: a no-op at run time that throws nothing java's own
      // raw read would not.
      //
      // THREE CONJUNCTS, and the third is where this was first written WRONG — which is worth
      // keeping, because the wrong version is the one that reads as obviously right. `onShim` and
      // `wildcardElement(recv.tpe)` say the receiver is one of THIS PHASE's targets carrying a sole
      // wildcard argument — §4.56's rule that a phase may only conclude something about a type from
      // what the phase itself did to it. The third was `t.tpe.isInstanceOf[TypeBounds]`, *the
      // result IS the capture*, and it fired NOWHERE: the frontend records java's own answer, so
      // the node already reads `java.lang.Object` on BOTH sides of the seam. That is CLAUDE.md §1's
      // sentence exactly — "a position-blind `transformType` has already remapped the reference
      // node's type, so a check reading node types reports ZERO on exactly the seam the scope
      // made" — and it is why `collection-boundary` cannot see this one either.
      //
      // So the disagreement is stated between the two things that actually differ: the node's
      // RECORDED result is `Object` (java substituting the capture's implicit bound) while the
      // callee's DECLARED result is not (a type VARIABLE, read literally off the class file, which
      // is the one reading §4.56 permits of an unowned signature). A method genuinely declared to
      // return `Object` fails the second half and takes no ascription, which is what keeps this
      // from putting an identity cast on every `Object`-returning shim call in the corpus.
      case _ if onShim && wildcardElement(recv.tpe) && capturedObjectRead(t) =>
        Some(Tree.Typed(t, TypeTree(t.tpe, t.origin), t.tpe, t.origin))
      case _ if onShim             => None
      // ---- java's BULK DEFAULTS, reached through `super` on a class THIS PHASE RE-PARENTED ----
      //
      // A class that DEFINES a collection inherits `containsAll`/`addAll`/`removeAll`/`retainAll`
      // from `java.util.AbstractCollection` and calls them through `super`, to delegate the general
      // case its own fast path does not cover. Re-parenting it onto a scala collection REMOVES the
      // implementation java was calling — the target has three of the four not at all and the
      // fourth at a different formal and a different result — so the phase owes one back
      // (`CLAUDE.md` §1's *an obligation the engine's own translation created*, `ENGINE-LIMITS.md`
      // K29). Nothing else can: no manifest key names a member the JDK declared.
      //
      // ==WHY IT IS THIS ARM AND NOT [[superIsThis]]==
      // The generic path already tries `super` → `this` and DECLINES here, correctly: `super.m` and
      // `this.m` name the same member only when nothing between them overrides `m`, and the class
      // in front of us overrides `m` — that override is the body the `super` call sits in, so
      // `this.m` would recurse into it. The licence for this arm is a DIFFERENT statement, and it is
      // why the rewrite may stand where the retry may not: the emitted call does not select `m` on
      // `this` at all. It calls a HELPER that REPRODUCES the JDK default's body, and the JDK's own
      // body dispatches virtually — `AbstractCollection.containsAll` is `for (o : c) if
      // (!contains(o))`, `addAll` is `for (E e : c) if (add(e))`, and the two mutators iterate
      // `iterator()` and remove through it. Every member they reach is a PUBLIC VIRTUAL member of
      // `this`, so a helper written over `this` computes exactly what `super` named.
      //
      // ==THE ARGUMENT IS MADE PER MEMBER, WHICH IS WHY THE SET IS A TABLE==
      // [[CollectionsTransform.VirtualJdkDefaults]] is those four and nothing else, each with the
      // JDK body it stands for written down beside it. This is emphatically not a general permission
      // to turn a `super` call into a `this` call: a JDK member whose body reads the receiver's own
      // FIELDS (`ArrayList.clone`, `AbstractList.subList`) is not reproducible from `this` at all,
      // and `superPlaced`'s refusal is the right answer for every member not in the table.
      //
      // ==TWO STRUCTURAL CONJUNCTS, both the phase's OWN record (§4.56)==
      // See [[superLostItsDefault]]. Neither is a name test, and the second is the one that keeps
      // this off a `super` that still HAS a target: a program-declared ancestor between the class
      // and the JDK type may declare `m` itself, in which case `super.m` names code this port still
      // emits and substituting the helper would change what the program does, silently.
      case (n, List(c), Kind.Seq | Kind.Set)
        if onSuper && CollectionsTransform.VirtualJdkDefaults.contains(n)
           && sym(CollectionsTransform.VirtualJdkDefaults(n)) != SymId.None
           && superLostItsDefault(recv, n) =>
        val f = sym(CollectionsTransform.VirtualJdkDefaults(n))
        recv match { case Tree.Super(cls, _, _) => superDefaults += ((cls, m, n)); case _ => () }
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(thisOf(recv), c), f, t.tpe, t.origin))
      // ---- `java.util.Stack`, whose target DECLARES four of its five (see [[Kind.Stack]]) ----
      //
      // `push`/`pop`/`peek`/`search` are members of the shim with java's own names, arity and
      // contracts, so the faithful rewrite is NO rewrite — and saying so explicitly is the point of
      // these two arms rather than an omission. Left to fall through, `peek()` would reach the
      // `Kind.Seq` arm below, which answers the DEQUE `peek`: the FIRST element and `null` when
      // empty, where java's `Stack.peek()` is the LAST and THROWS. That is a wrong answer at both
      // ends of one call with no compile error, and it is the reason `Kind.Stack` exists at all.
      //
      // `empty()` is the one member that cannot be java's: scala's collection API already declares
      // `empty` — the FACTORY, with an incompatible result type — so the shim may not redeclare it
      // and the call is renamed to the predicate that asks the same question.
      case ("empty", Nil, Kind.Stack) => Some(Tree.Select(recv, isEmptySym, t.tpe, t.origin))
      case ("push" | "pop" | "peek" | "search", _, Kind.Stack) => None
      // ---- `java.util.Optional{Int,Long,Double}`, whose target is an `Option[…]` alias ----
      //
      // Pure renames, all of them EXCEPT `orElse` — see its own arm. The VALUE is the same object
      // and only the member name differs.
      // `get`/`isDefined` are PARAMETERLESS on `Option` where java's are nilary, so they are a
      // `Select` and not an `Apply` — the same distinction `parenless` exists for, met at names
      // that also change. `orElseThrow()` is `get` because java's no-argument overload throws
      // `NoSuchElementException` on an empty optional and so does `Option.get`; the SUPPLIER
      // overload is a different exception and gets no arm, so it reaches `jdk-surface`.
      case ("getAsInt" | "getAsLong" | "getAsDouble" | "orElseThrow", Nil, Kind.Opt) =>
        Some(Tree.Select(recv, getSym, t.tpe, t.origin))
      case ("isPresent", Nil, Kind.Opt)      => Some(Tree.Select(recv, isDefinedSym, t.tpe, t.origin))
      // …and the ONE that is not a rename. `Optional.orElse(T other)` takes a VALUE — java
      // evaluates the argument at the call whatever the optional holds — while
      // `Option.getOrElse(=> B)` evaluates it only when empty. Rendered as `getOrElse` a
      // side-effecting or costly default runs in java and does not run in the port: same name,
      // same answer, different program, with a green compile and no count to see it
      // (`CLAUDE.md` §4.4). `JavaCollections.optionalOrElse` is `getOrElse` with a BY-VALUE
      // parameter, which restores java's evaluation at the call and nothing else.
      case ("orElse", List(d), Kind.Opt) if sym("optionalOrElse") != SymId.None =>
        val f = sym("optionalOrElse")
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv, d), f, t.tpe, t.origin))
      case ("ifPresent", List(f), Kind.Opt)  => Some(call(recv, foreachSym, List(f), t, so))
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
      // `list.listIterator()` / `listIterator(i)` — java's BIDIRECTIONAL cursor, which K23 refused
      // and which does not survive being re-read (`ENGINE-LIMITS.md` K23). The refusal is a
      // statement about `scala.collection.Iterator`; the RECEIVER here is a `mutable.Buffer`, whose
      // indexed read, indexed update, insert and remove ARE `ListIterator`'s contract. So this is
      // not a member with nothing to map onto, it is a member with nothing to map onto IN SCALA'S
      // ITERATOR — and §4.5's answer to exactly that is a standalone shim carrying java's shape.
      //
      // `over` writes THROUGH to the same buffer the caller holds, which is the capability a
      // detached copy loses and the whole reason java's interface exists. `Kind.Seq` only: java
      // declares `listIterator` on `List` and nowhere else, so a `Set`/`Map` receiver never had it.
      case ("listIterator", args @ (Nil | List(_)), Kind.Seq | Kind.Stack)
        if listIteratorOverSym != SymId.None =>
        Some(Tree.Apply(Tree.Ident(listIteratorOverSym, TypeRepr.NoType, so), recv :: args,
                        listIteratorOverSym, t.tpe, so))
      // `c.spliterator()` — K23's OTHER refusal, and the one that stayed refused when
      // `listIterator` did not. That refusal is exact about the protocol (a `Spliterator` is a
      // parallel DECOMPOSITION whose only consumer is `java.util.stream`, which this phase
      // collapses) and its stated evidence — `buf.asJava.spliterator()` reports NEITHER `ORDERED`
      // nor `SIZED` — does NOT reproduce: the converter's wrapper reports the same characteristics
      // these helpers do (`JavaCollectionsSpec`). So delegating would have worked too, and the
      // reason to state java's answer rather than inherit it is that the characteristics then
      // follow JAVA'S DECLARATION and not scala's converter.
      //
      // Either way nothing about streams is modelled, because java's own answer is a DEFAULT
      // METHOD whose characteristics are written down: `Collection` passes `0`,
      // `List` passes `ORDERED`, `Set` passes `DISTINCT`, and `Spliterators.spliterator` ORs in
      // `SIZED | SUBSIZED` for all three. The owner a call resolved at is the receiver's KIND, which
      // this arm is already keyed on — so the emission is java's declaration at that owner and not
      // a wrapper's accident. `Kind.Entry`/`Kind.Opt` never had the member and fall through.
      case ("spliterator", Nil, k @ (Kind.Seq | Kind.Stack | Kind.Set))
        if orderedSpliteratorSym != SymId.None =>
        val f = if k == Kind.Set then distinctSpliteratorSym else orderedSpliteratorSym
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv), f, t.tpe, so))
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
      // `m.keySet()` and `m.entrySet()` are java's two LIVE, WRITE-THROUGH views of a map, and both
      // are the provenance problem `values()` above states, met at the members where the gap is
      // widest. The node's type is the RETYPED `java.util.Set` — `mutable.Set` — while the scala
      // emitted for it was `m.keySet` (a `scala.collection.Set`, one capability short) and, for
      // `entrySet`, the MAP itself (an `Iterable[(K, V)]`, not a `Set` at all).
      //
      // The phase carried two local answers for that disagreement, one per position it could reach
      // — retype a `val` initialised from `keySet`, refuse to wrap a `keySet` coercion SOURCE — and
      // a java method whose declared result is `Set<K>` is a THIRD position that neither reaches.
      // So the disagreement is removed where it is MADE: the rewrite now emits a value that really
      // has the type the node claims, and every position is answered at once — a return, an
      // argument, a `val`, a branch of a conditional — for the reason the `values()` arm gives
      // three lines up. Measured at 11 errors on one port.
      //
      // The VIEW and not a copy, and not `to(mutable.Set)`: java's is live in both directions, so a
      // snapshot silently changes what a later `put` is observed to do — the same argument the
      // `entrySet` rewrite has always made for handing back the map rather than `m.toSet`.
      case ("keySet", Nil, Kind.Map) if sym("keySetView") != SymId.None =>
        Some(staticCall(sym("keySetView"), List(recv), t, so))
      case ("entrySet", Nil, Kind.Map) if sym("entrySetView") != SymId.None =>
        Some(staticCall(sym("entrySetView"), List(recv), t, so))
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
      // [[wildcardMapCall]] for why the ordinary rewrite cannot be used there; and the SAME three
      // helpers answer the other half of that seam, an argument still carrying java's `Object`
      // PROBE at a receiver whose key type this phase moved ([[objectProbe]]). `keyArg` runs FIRST,
      // so a coercion it can strip is stripped and never reaches the helper.
      case (n, List(key), Kind.Map) if wildcardMapCall(n, recv, keyArg(key, recv)) || probeMapCall(n, keyArg(key, recv), recv) =>
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
      // ---- SE8's default methods on the interfaces, which the retyping moved the ground under ----
      //
      // Every one of these is a member java added to `List`/`Map`/`Collection` in 8, so a library
      // written since uses them as readily as `get` — and each has a scala member that LOOKS like
      // it and means something else, which is why each is a helper rather than a rename. The
      // contracts are stated where the helpers are; the one-line reason per arm:
      //
      //   - `sort` mutates IN PLACE and scala's `sorted` is a copy (the same helper the
      //     `Collections.sort` static already reaches, because SE8 made that static delegate here);
      //   - `computeIfAbsent` treats a `null` VALUE as absent and records nothing when the factory
      //     answers `null`; `getOrElseUpdate` does neither;
      //   - `removeIf` keeps the COMPLEMENT of `filterInPlace` and returns java's `boolean`;
      //   - `containsValue`/`containsAll` ask the PROBE's `equals` where scala asks the element's;
      //   - `ensureCapacity` is a hint with no observable behaviour at all.
      case ("sort", List(c), Kind.Seq) if sym("sort") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("sort"), TypeRepr.NoType, so), List(recv, c),
                        sym("sort"), t.tpe, t.origin))
      case ("computeIfAbsent", List(key, f), Kind.Map) if sym("computeIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("computeIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), f), sym("computeIfAbsent"), t.tpe, t.origin))
      // …and the SET spelling is a different helper rather than an overload: the two erase alike, so
      // scala cannot hold both under one name, and picking by the receiver's KIND here puts the
      // choice in the emitted call instead of in a run-time dispatch.
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
      // …and `containsAll`'s two MUTATING siblings, which had no arm at all. Neither is a
      // near-miss the way the SE8 members above are: `mutable.Buffer` has no `removeAll` and no
      // `retainAll`, so the untranslated call is a `Not Found` rather than a silent divergence —
      // and the reason each needs a HELPER rather than a rename is that the nearest scala members
      // answer a different question. `--=` is `subtractAll`, which removes ONE occurrence per
      // element of the argument where java removes every occurrence (`[a,b,a] --= [a]` is `[b,a]`
      // and java's `removeAll` gives `[b]`); `filterInPlace` keeps the complement and returns the
      // collection where java returns the `boolean` callers branch on. The helpers state both,
      // with java's own probe direction — see their docs, which is where the contract lives.
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
      // `addAll` from a WILDCARD-elemented source is not `++=` — see [[wildcardElement]]. Java's
      // `List<?>` is `List<? extends Object>`, so reading it as `Object` is sound and java accepts
      // `list.addAll(valueList)`; scala's `?` is bounded by `Any`, so `Buffer[?]` is an
      // `IterableOnce[Any]` and `++=` on a `Buffer[Object]` reads
      // `Required: IterableOnce[Object]`. The helper states java's read explicitly and returns
      // java's own `boolean` besides.
      case ("addAll", List(c), _) if (wildcardElement(c.tpe) || standaloneSource(c.tpe)) &&
                                     sym("addAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("addAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("addAll"), t.tpe, t.origin))
      // …and java's POSITIONAL `addAll(int, Collection)`, which is `insert`'s bulk sibling and NOT
      // this table's one-argument `addAll` at all. Left to fall through, the call reached scalac as
      // `buf.addAll(0, c)` against `Growable.addAll(IterableOnce)` — which scala ACCEPTS by
      // AUTO-TUPLING, so java's two arguments become one `(Int, Collection)` pair. It happens to be
      // a compile error at this element type; at an element type of `Any` it is a program that
      // appends a pair where java inserted a collection, with nothing to report it (§4.4 at an
      // arity). `Kind.Seq`/`Kind.Stack` only: java declares the positional form on `List`.
      case ("addAll", List(i, c), Kind.Seq | Kind.Stack) if sym("insertAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("insertAll"), TypeRepr.NoType, so), List(recv, i, c),
                        sym("insertAll"), t.tpe, t.origin))
      case ("addAll" | "putAll", List(c), _)    => Some(infix(recv, opPlusPlusEq, List(c), t, so))// xs ++= c
      // …the SET half of [[objectProbe]]'s seam. `Collection.contains` needs no rewrite at all at an
      // ordinary argument — scala's `Set.contains` is java's own hash lookup, asking the PROBE's
      // `equals` exactly as `HashMap.getNode` does — so this arm exists solely for the widened
      // probe, and declines everywhere else. `Set.remove` takes the helper rather than `-=` for the
      // same reason PLUS java's `boolean` result, which `-=` cannot answer.
      case ("contains", List(x), Kind.Set) if setContainsSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setContainsSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set) if setRemoveSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setRemoveSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set)        => Some(infix(recv, opMinusEq, List(x), t, so)) // xs -= x
      case ("containsKey", List(key), Kind.Map) => Some(call(recv, containsSym, List(keyArg(key, recv)), t, so))
      // …and a STACK is a `List` for everything the five LIFO arms above did not take, because java
      // says so: `Stack extends Vector extends List`, and the target is the same `Buffer` a
      // `java.util.List` maps to. So `stack.get(i)`, `stack.remove(x)`, `stack.subList(a, b)` and
      // the rest are answered by the arms already above, re-entered at `Kind.Seq`.
      //
      // A RE-ENTRY and not a second copy of the table: two spellings of `get` is two things to keep
      // in step, which is the mistake `superIsThis`'s own retry avoids the same way. It terminates
      // because `Kind.Seq` is not `Kind.Stack`, and the arms keyed on `_` have already been offered
      // this call at `Kind.Stack` — they answer identically either way.
      case _ if k == Kind.Stack                 => rewrite(Kind.Seq, recv, m, so, t)
      case _                                    => None
    if !onSuper then out
    else
      // …and where the shape it built puts `super` somewhere scala has no position for, there is
      // ONE more answer before the refusal: stand on `this` instead. It is exact only under the
      // whole-program condition [[superIsThis]] states, and the retry goes back through this same
      // function so the rewritten term is built by the SAME arm — a second spelling of any arm here
      // is a second thing to keep in step. The recursive call's own receiver is not a `Super`, so it
      // returns directly and the `superPlaced` filter below is then trivially satisfied; it is
      // applied anyway, because a filter that holds by construction is free and a filter omitted on
      // the grounds that it holds is the omission `superPlaced` exists to prevent.
      out.filter(superPlaced).orElse(
        if superIsThis(recv, name) then rewrite(k, thisOf(recv), m, so, t).filter(superPlaced)
        else scala.None)

  /** may a rewrite that cannot stand on `super` stand on `this` instead — i.e. do `super.m` and
    * `this.m` name THE SAME MEMBER for every value this expression can have?
    *
    * `super.m` is java's non-virtual call of the nearest inherited `m`. `this.m` is the virtual
    * one, so the two agree exactly when nothing between them can override: neither the class
    * itself, nor any class IN THIS PROGRAM that extends it, declares `m`. Both halves are needed —
    * an override on the class itself makes `this.m` recurse into it, and one on a SUBCLASS makes an
    * instance of that subclass dispatch somewhere `super.m` never would.
    *
    * "In this program" is the honest scope and it is stated rather than assumed: a class the port
    * emits can be extended by code the port never sees, and no whole-program question can answer
    * for that. What makes it admissible here is that the alternative is not a correct emission but
    * NO emission — the refused rewrite leaves a call that does not compile — so the choice is
    * between an exact answer for every subclass the program declares and no answer at all.
    *
    * Both walks read the class DEFINITIONS rather than the symbol table, because the question is
    * "does a declaration exist", which is what a `ClassDef`'s body is; and the subclass walk is
    * transitive (`A extends B extends C`), since an override two levels down dispatches exactly as
    * one level down does. */
  private def superIsThis(recv: Term, member: String)(using p: Program): Boolean = recv match
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
      /** does `c` reach `cls` through its parents? Fuel-bounded, and a walk that exhausts its fuel
        * counts as REACHING — the conservative answer, since the caller refuses on `true`. */
      def below(c: Tree.ClassDef, fuel: Int): Boolean =
        fuel <= 0 || parentsOf(c).exists(s => s == cls || byId.get(s).exists(below(_, fuel - 1)))
      byId.get(cls).exists(!declares(_)) &&
        !all.exists(c => c.symbol != cls && declares(c) && below(c, 64))
    case _ => false

  /** did the RE-PARENTING remove the implementation this `super.<member>` named — i.e. is the
    * member's nearest implementation the JDK DEFAULT that the mapping's target does not have?
    *
    * Both conjuncts are the phase's own record rather than a name test (§4.56), and each answers a
    * different half of the question:
    *
    *   - **this phase RE-PARENTED the class.** [[parentClash]] is the record of exactly that — every
    *     class whose emitted parent this phase minted, transitively over the program's own parents —
    *     and it already excludes the three shapes that KEPT java's parent (a declaration the scope
    *     held back, a target that cannot BE a parent, a parent the mapping does not cover). Where it
    *     has no entry the class still extends what java wrote, `super.m` still resolves, and there
    *     is nothing to supply;
    *   - **no program-declared ANCESTOR declares the member.** This is the conjunct with teeth. A
    *     `super.removeAll(c)` two levels down names the SUPERCLASS's `removeAll` where the library
    *     declares one, and that member is code this port still emits — the JDK default is not what
    *     `super` meant at all, and substituting the helper would silently run a different program.
    *     Asked UPWARD from the class's parents, transitively, and never of the class itself: the
    *     class's own override IS the body this call sits in.
    *
    * Fuel exhaustion counts as DECLARED — the conservative arm, since the caller refuses on `true`
    * — which is [[superIsThis]]'s own convention at its own walk, and for its own reason: a cycle in
    * the parent edges is something java forbids and a corrupt tree could still hand over. The walks
    * read the class DEFINITIONS rather than the symbol table because the question is "does a
    * declaration exist", which is what a `ClassDef`'s body is. */
  private def superLostItsDefault(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      parentClash.get(cls).exists(_.kinds.nonEmpty) && !ancestorDeclares(cls, member)
    case _ => false

  /** does any class this PROGRAM declares, strictly ABOVE `cls`, declare `member`? */
  private def ancestorDeclares(cls: SymId, member: String)(using p: Program): Boolean =
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

  /** the `this` standing where `recv`'s `super` stood — same class, same origin. Its TYPE is the
    * class's own, which is what every rewrite here reads the receiver's kind from
    * ([[inheritedKind]] already answers for a class that EXTENDS a mapped collection, which is the
    * only shape a `super` receiver can have). */
  private def thisOf(recv: Term): Term = recv match
    case Tree.Super(cls, tpe, so) => Tree.This(cls, tpe, so)
    case other                    => other

  /** does every `super` in this rewritten term stand where scala allows one — as the QUALIFIER of a
    * member selection, and nowhere else?
    *
    * Scala's grammar admits `super` in exactly one position. Java has no such restriction, so an
    * inherited call on a class that EXTENDS a retyped collection can be rewritten into a shape that
    * puts it somewhere illegal: `entrySet()` maps to the RECEIVER ALONE (`for (e <- super)`) and the
    * `Seq` `get` maps to an application of it (`super(i)`). Both are E040 SYNTAX errors, which are
    * strictly worse than the type errors they replace — a syntax error cannot be attributed to a
    * member and can take the rest of the file with it.
    *
    * Asked of the RESULT rather than of the arm, which is the whole point: a rewrite added later is
    * covered by construction, and no arm can reintroduce the failure by omission. That is the
    * property the previous BLANKET refusal was bought for, kept without the cost — `super.putAll(m)`
    * and `super.contains(k)` are legal and now translate, while the two shapes above stay
    * untranslated and fail to compile naming the member (M6).
    *
    * The walk is `StandardTraversal`'s (CLAUDE.md §3): a hand-rolled recursion that stopped one node
    * short would answer "safe" for the shape this test exists to catch. */
  private def superPlaced(t: Term)(using Program): Boolean =
    var bad = false
    val scan = new Phase:
      def name = "super-placement"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          // a `super` reached as a Select's qualifier is the one legal position; every OTHER
          // occurrence is found by the default arm below, because the traversal visits the
          // qualifier as a term of its own.
          case Tree.Select(_: Tree.Super, _, _, _) => x
          case _: Tree.Super                       => bad = true; x
          case _                                   => x
    // the qualifier of a legal Select is still visited on the way down, so the exemption above has
    // to REPLACE the descent rather than sit beside it: strip the legal ones first, then scan.
    StandardTraversal.mapTerm(scan, stripLegalSuper(t))
    !bad

  /** replace every LEGAL `super.member` with a marker the placement scan does not object to, so the
    * scan sees only the occurrences that stand somewhere else. `Tree.This` is chosen because it is
    * the one node with exactly `super`'s legal positions and one more, and nothing downstream ever
    * sees this term — it is built for the scan and discarded. */
  private def stripLegalSuper(t: Term)(using Program): Term =
    val strip = new Phase:
      def name = "super-strip"
      override def transformTerm(x: Term)(using Program): Term = x match
        case s @ Tree.Select(sup: Tree.Super, m, tp, o) => Tree.Select(Tree.This(SymId.None, sup.tpe, sup.origin), m, tp, o)
        case _                                          => x
    StandardTraversal.mapTerm(strip, t)

  /** is this source's SOLE element type an unnameable wildcard — the whole of F11?
    *
    * `java.util.List<?>` means `List<? extends Object>`: java's unbounded wildcard has `Object` as
    * its implicit upper bound, so every read off one yields an `Object` and
    * `list.addAll(valueList)` type-checks with no cast anywhere in sight. Scala's `?` is bounded by
    * `Any`, which is strictly wider — `Buffer[?]` is an `IterableOnce[Any]`, and `++=` on a
    * `Buffer[Object]` reads `Found: Buffer[?] / Required: IterableOnce[Object]`.
    *
    * Widening scala's `?` is not the fix and is a measured dead end: G2 explored that whole design
    * space and settled on rendering a raw generic as `[?]` everywhere, which is also what the
    * reference port emits. So the difference is stated at the ONE operation it blocks, by a helper
    * that performs java's own read, rather than by moving what a wildcard means.
    *
    * Narrow deliberately: only a sole type argument that IS a `TypeBounds`. A source with a real
    * element type conforms through `IterableOnce`'s covariance and stays the idiomatic `++=`,
    * which is what every other port in the corpus emits today. */
  private def wildcardElement(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(_, List(_: TypeRepr.TypeBounds)) => true
    case _                                                     => false

  /** …and the OTHER reason `++=` cannot serve java's `addAll`: the SOURCE is one of this phase's
    * STANDALONE targets, which is not a `scala.collection` type at all.
    *
    * [[wildcardElement]] above is about the ELEMENT and was, for as long as this arm had one guard,
    * the only thing deciding a question about the CONTAINER — §4.56's fast-path guard read at a
    * rewrite. `++=` is right whenever the argument is a scala-collection target, and it is wrong for
    * EVERY element type when the argument is a `balticporter.runtime` shim: `JavaCollection` extends
    * `JavaIterable` and nothing else (§4.5 is why), so it is not an `IterableOnce` and never will be.
    *
    * The shape reaches the corpus wherever java's own `List <: Collection` edge carried the value —
    * a `DataKey<Collection<X>>` read back and appended to an `ArrayList<X>` — which is the same
    * broken edge `CollectionInternalCheck` counts and is exactly the population `++=` cannot serve.
    * The union-typed helper this routes to already takes `IterableOnce[?] | JavaIterable[?]`, so
    * there is nothing to build: the arm simply has to be reachable.
    *
    * Read from the phase's OWN record of the types it minted (`shimSyms`), never from a package
    * name (§4.56). */
  private def standaloneSource(t: TypeRepr): Boolean = headSym(t).exists(shimSyms.contains)

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
  /** does this type MENTION a wildcard — at any depth?
    *
    * Deliberately NOT a nameability test, which is what it looks like and what it was first written
    * as. A wildcard-APPLIED type is nameable (`Class[? <: N]` is a type a call site can write), so
    * this answers a narrower question for [[wildcardMapCall]]'s third condition only: *could scala's
    * INVARIANCE bite at this key*. It is paired there with an equality against the probe's own type,
    * because a wildcard that both sides spell identically unifies and needs nothing.
    *
    * Complete over `TypeRepr` rather than over the two constructors this port happens to need — a
    * partial walk here would answer "no invariance risk" for a shape nobody enumerated. */
  private def mentionsWildcard(t: TypeRepr): Boolean = t match
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

  /** THREE conditions, and the third is not the same question as the first two — which two ports
    * had to measure before it could be stated.
    *
    * The obvious reading of `Map<Class<? extends N>, H>` is that its key is unnameable one
    * constructor deeper than the one-level test looked, i.e. that the original predicate was a
    * partial type walk. **That reading is wrong**, and the corpus says so twice: a wildcard-APPLIED
    * type is perfectly nameable and inhabited — `Class[? <: N]` and `Item[?]` are both types a call
    * site can write down — so a walk that fires on "mentions a wildcard" is not answering this
    * function's question at all. What actually fails at `getAction(Class<?> nodeClass)` is that
    * scala's `Map[K, V]` is INVARIANT in `K`, so the probe's own `Class[?]` does not conform to the
    * key's `Class[? <: N]`; java, whose `get` takes `Object`, never asked.
    *
    * So the three conditions are:
    *
    *   - a BARE capture KEY (`Map<?, V>`) — genuinely unnameable, and what this function was
    *     originally written for. Unchanged;
    *   - a BARE capture VALUE (`Map<K, ?>`) — nameable as a key, but `get`'s ascribed `null` default
    *     ([[dflt]]) cannot be written at a capture. Also unchanged, and the reason the original
    *     predicate looked at both arguments;
    *   - a key that merely CONTAINS a wildcard, where the PROBE's rendered type is not the key's.
    *     Invariance makes those two irreconcilable and java had no such rule. Where they are EQUAL,
    *     scala unifies and the ordinary rewrite is exactly right.
    *
    * The third is an equality, never a conformance oracle (`CLAUDE.md` §4.56): `TypeRepr` equality
    * is decidable here and a subtype test is not. It over-approximates in one direction only — a
    * probe at a strict subtype of a wildcard-bearing key takes the helper it did not need — which is
    * emitted text and never a wrong answer.
    *
    * Both looser spellings were MEASURED, on ports that have no such seam at all: deep on the value
    * as well routed libGDX core's `Map<Application, Array<GLFrameBuffer<?>>>` through the helpers
    * (6 members, 0 errors), and deep on the key without the equality routed jbump's
    * `HashMap<Item, Rect>`, whose raw `Item` key renders `Item[?]` and whose probe is an `Item[?]`
    * that conforms perfectly (9 members, 0 errors). Neither is WRONG and both are the review noise
    * `CLAUDE.md` §1 refuses — an over-approximation moves no count, so the diff is the only thing
    * that can ever see it. */
  private def wildcardMapCall(name: String, recv: Term, key: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (actualOf(recv)._1 match
        case TypeRepr.AppliedType(_, List(k, v)) =>
          k.isInstanceOf[TypeRepr.TypeBounds] || v.isInstanceOf[TypeRepr.TypeBounds] ||
            (mentionsWildcard(k) && key.tpe != k)
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)

  /** …and the SAME three members reached with java's UNTYPED PROBE still on the argument.
    *
    * [[wildcardMapCall]] is about the RECEIVER: the key type is a capture nobody can name. This is
    * about the ARGUMENT, and it is the other half of one seam — java declares `get`, `containsKey`,
    * `remove`, `Collection.contains` and `Set.remove` over `Object` ON PURPOSE, because the lookup
    * is BY VALUE and a probe of an unrelated type is meant to miss rather than to fail to compile.
    * The retyping moves the receiver to a scala collection whose members are typed at `K`/`A`, and
    * the probe then does not fit the slot it fitted in java. Two ways it arrives, one shape:
    *
    *   - a class that IMPLEMENTS `java.util.Map<String, T>` must declare `remove(Object o)` and
    *     delegate — the parameter is java's own and there is nothing to strip;
    *   - the frontend's erasure coercion (`typeParamToObject`, ENGINE-LIMITS G14) widened a
    *     type-parameter or wildcard-read key to `Object` because THAT is what java's formal said.
    *     The mint is right for a call to a java `Map`; what invalidated it is this phase moving the
    *     receiver, which is `keyArg`'s own argument at the coercion it cannot strip.
    *
    * The test is STRUCTURAL and asks only the one question a phase can answer with no conformance
    * oracle (CLAUDE.md §4.56): `java.lang.Object` is the TOP of java's reference hierarchy, so an
    * argument at that type conforms to a scala element type only when the element type is `Object`
    * too. That is a fact about the two type systems, not a guess about this program — and it is
    * asked of THIS RUN's interned symbol rather than of a name.
    *
    * NOT a cast to the element type, which is the translation that compiles and means something
    * else: `o.asInstanceOf[String]` inserts a `checkcast` and throws where java's `map.get(o)`
    * answers `null`. The helper widens the PROBE POSITION, which is erased, so java's own lookup
    * runs (CLAUDE.md §4.4). */
  private def objectProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    objectSym != SymId.None && headSym(arg.tpe).contains(objectSym) &&
      want.exists(w => w != TypeRepr.NoType && !headSym(w).contains(objectSym))

  /** …and the THIRD face of the SAME seam: a probe at a PROPER ANCESTOR of the element type.
    *
    * [[objectProbe]] asks its question with no conformance oracle because `java.lang.Object` is the
    * TOP of java's reference hierarchy — that is what makes it exact. It is therefore silent about
    * every OTHER supertype, and java's `Object` formal admits all of them:
    * `Map<JekyllTag, String>.containsKey(node)` at a `Node` is ordinary java (the lookup is BY VALUE
    * and a probe of an unrelated type is meant to MISS), and scala's `Map[K, V]` is INVARIANT in `K`,
    * so the retyped receiver's member no longer takes it. `Found: Node / Required: JekyllTag`, which
    * only scalac ever sees — the probe is not at `Object`, so `objectProbe` correctly declines, and
    * `wildcardMapCall` correctly declines too because nothing here is a wildcard.
    *
    * The question is answered STRUCTURALLY and needs no subtype test (CLAUDE.md §4.56): it walks
    * THIS RUN's own `extends` edges from the ELEMENT type up, and answers true only where it reaches
    * the PROBE's head. That is not an over-approximation in either direction — if the element type
    * descends from the probe's type then the probe is provably not a subtype of the element (they
    * are distinct here), so the ordinary rewrite could never have been right; and a probe the walk
    * cannot account for takes the ordinary rewrite exactly as before, which is the conservative arm
    * §4.56 asks for when a parse cannot answer.
    *
    * NOT a cast to the element type, for [[objectProbe]]'s reason and with its measurement:
    * `node.asInstanceOf[JekyllTag]` inserts a `checkcast` and THROWS where java's probe answers
    * `false`. The helper widens the PROBE POSITION, which is erased. `ENGINE-LIMITS.md` K24's third
    * face. */
  private def ancestorProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    (headSym(arg.tpe), want.flatMap(headSym)) match
      case (Some(a), Some(e)) if a != SymId.None && e != SymId.None && a != e =>
        def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
          case tt: TypeTree => headSym(tt.tpe)
          case term: Term   => headSym(term.tpe)
        }
        // fuel-bounded, and an EXHAUSTED walk answers FALSE — the conservative arm here, since a
        // `true` routes the call to the helper and the caller's default is the ordinary rewrite.
        def reaches(id: SymId, fuel: Int): Boolean =
          fuel > 0 && classDefsBySym.get(id).exists(c =>
            parentsOf(c).exists(s => s == a || reaches(s, fuel - 1)))
        reaches(e, 64)
      case _ => false

  private def probeMapCall(name: String, key: Term, recv: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (objectProbe(key, keyType(actualOf(recv)._1)) || ancestorProbe(key, keyType(actualOf(recv)._1)))

  private def probeSetCall(x: Term, recv: Term)(using Program): Boolean =
    objectProbe(x, elemType(actualOf(recv)._1)) || ancestorProbe(x, elemType(actualOf(recv)._1))

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

  /** a one-argument collection's ELEMENT type — [[keyType]]'s counterpart at a `Set`/`Buffer`. */
  private def elemType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(e)) => Some(e)
    case _                                => None

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
    if literalEmpty then (t.tpe, false)
    else CollectionsTransform.scopedType(t, literal).map(_ -> true).getOrElse(t.tpe -> false)

  // -------------------------------------------------------------------------------------------
  // THE CLASH THIS PHASE'S OWN PARENT MADE — `CLAUDE.md` §4.5, met at a CALL
  // -------------------------------------------------------------------------------------------
  //
  // §4.5 says a parent adds MEMBERS and an extension adds a view, and it says so about the SHIMS.
  // The same sentence governs what this phase does to a class the PROGRAM declares: `OrderedMap
  // implements java.util.Map` is emitted `extends scala.collection.mutable.Map`, so the class now
  // inherits `remove(key: K): Option[V]` beside the `remove(o: Object): V` java made it declare —
  // java's by-value lookup, which `ENGINE-LIMITS.md` K24 kept for exactly the reason java wrote it.
  //
  // Java's candidate set at `map.remove("0")` was ONE member. Scala's is TWO and a `String` matches
  // both: `E051 Ambiguous overload`, on a call java resolved without hesitating. Nothing in the port
  // is wrong — the member has to stay, the parent is what makes every retyped slot conform — so the
  // obligation is the PHASE'S, exactly as `CLAUDE.md` §1 says of a residue a translation created.

  /** every class the program declares whose emitted PARENT this phase minted, with the [[Kind]] of
    * the java parent that produced it.
    *
    * Read off the ORIGINAL units, in [[run]], because after the traversal the parent is already the
    * scala target and the java type it came from is gone. `StandardTraversal.allClassDefs` and not a
    * `body.collect` (§3): a class nested inside a method body is a `Tree.ClassDef` in a
    * `BlockStatement` and a members-only walk answers *there is no nested type here*.
    *
    * ==AND `allAnonClasses` BESIDE IT, because an anonymous body is not a `ClassDef` at all==
    * `new ReversiblePeekingIterable<Node>() { … }` is a `Tree.AnonClass` hanging off a `Tree.New`,
    * with its parent written at the `new` and no `parents` list of its own — so no node kind gets it
    * out of `allClassDefs`, and every reader of this record answered *this type extends nothing this
    * phase touched* about a body whose one supertype the phase had just re-parented. The member half
    * showed as an `E037` on a `forEach` the shim does not declare (`ENGINE-LIMITS.md` K28); the four
    * OTHER readers of this record — `pinnedByObject`, `superLostItsDefault`, `mintedSourceKind`,
    * `coerce` — were being told the same non-fact and had no error to show for it, which is §5's
    * *widen a guard and measure it on the ports it was not aimed at* exactly.
    *
    * Three kinds of parent are excluded and each for a reason this phase already states elsewhere:
    * one the SCOPE held back keeps java's parent ([[restoreExcluded]]), one whose target cannot BE a
    * parent keeps java's ([[restoreUninheritableParents]]), and one the mapping does not cover was
    * never re-parented at all. A STANDALONE target is not excluded but RECORDED APART, in `shims`:
    * it contributes no member at a type parameter — a shim carries JAVA's own shape and arity by
    * construction (§4.5), which is the whole reason it is standalone — and it is precisely what says
    * a value of this class already CONFORMS at a shim-typed slot.
    *
    * ==TRANSITIVE over the program's own parents==
    * A class two hops from the mapped interface is re-parented exactly as much as one hop is: the
    * `extends` clause that names `java.util.Set` may sit on an abstract base this library declares,
    * and `CLAUDE.md` §4.56's fast-path rule is that a test written for the shape in front of you
    * silently answers for every shape added since.
    *
    * TWO APPROXIMATIONS, both stated rather than hidden, and both of which only ever DECLINE. A
    * cycle in the parent edges — which java forbids for classes and which a corrupt tree could still
    * hand over — takes the empty arm at the repeat, and a class memoised while one was open keeps
    * that under-approximate answer: no pin fires and no wrap is inserted, which is the honest
    * compile error rather than a wrong translation. And the `probes`/`tparams` an ancestor
    * contributes come across UNSUBSTITUTED, so a probe that is the ANCESTOR's type parameter
    * resolves against nothing and the refusal declines, leaving the loud residue rather than firing
    * wrongly. */
  private def declaredParentKinds(p: Program): Map[SymId, MintedParents] =
    given Program = p
    def tpeOf(x: Term | TypeTree): TypeRepr = x match
      case t: TypeTree => t.tpe
      case t: Term     => t.tpe
    val classes = p.units.flatMap(StandardTraversal.allClassDefs)
    val anons   = p.units.flatMap(StandardTraversal.allAnonClasses)
    /** (what this type extends, what type parameters it declares) — the two things `resolve` reads,
      * for BOTH kinds of type-like body. An anonymous class has exactly one parent, written at the
      * `new` rather than in its own node, and NO type parameters: java's grammar has no place to
      * declare one on an anonymous class, so `Nil` here is exact and not an approximation. */
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
            // approximation `probes` states and is useless to a bridge — `IndexedItemSetMapBase[K,
            // S, M] implements IndexedItemSetMap[K, S, M]`, whose interface extends `Map<K, S>`, has
            // to arrive here as `Map[K, S]` in the SUBCLASS's variables or the emitted signature
            // names types that are not in scope (§4.56). `ParentSubst` is the one substitution in
            // the engine and is what the emitter already writes forwarded members with.
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
            // …and the DUPLICATE RELATION among this class's OWN clauses (K28.1). Both halves are
            // in hand exactly here — the kind parents with the java types they came from, and the
            // shim parents beside them — which is what holding the two side by side is FOR. The
            // question is asked of the DIRECT clauses only: an inherited kind's element type is the
            // ancestor's, unsubstituted (`probes` carries the same approximation for the same
            // reason), and reading it here would be a guess where declining leaves the `E164` the
            // compiler already states.
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

  private def firstTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, a :: _) => Some(a)
    case _                               => scala.None

  /** every type argument the clause writes — `Nil` for a RAW one, which is exactly the arm the
    * synthesis declines on: a raw `implements Map` names no key and no value, and inventing
    * `java.lang.Object` for them would be §4.6's fabricated fact at the emitted signature. */
  private def typeArgs(t: TypeRepr): List[TypeRepr] = t match
    case TypeRepr.AppliedType(_, as) => as
    case _                           => Nil

  /** does the KIND parent really iterate what the SHIM parent says this class iterates?
    *
    * [[CollectionsTransform.SubsumesShim]] says a `scala.collection` target is a supertype
    * answering for `JavaIterable`'s one member; that is a claim about the TRAIT, and it is only a
    * claim about THIS CLASS where the two clauses agree on the element. `implements Map<K,V>,
    * Iterable<Map.Entry<K,V>>` is the shape java writes and the two DO agree — `mutable.Map[K,V]`
    * iterates `(K, V)`, which is what `Map.Entry<K,V>` is retyped to. `implements Map<K,V>,
    * Iterable<String>` is ordinary java too (java's `Map` declares no `iterator()` at all, so
    * nothing forces the element), and there the relation is NOT carried: dropping the clause would
    * silently change what `for (x <- xs)` yields, with a green compile and no count moving.
    *
    * Asked in JAVA's own types, because [[declaredParentKinds]] reads the ORIGINAL units — which is
    * also why the `Map` arm compares against `java.util.Map.Entry` rather than against `Tuple2`:
    * the mapping's own [[Kind.Entry]] row is what relates the two, so this is the phase reading its
    * own record (§4.56) and not a second opinion about java.
    *
    * DECLINES on anything it cannot decide — a RAW clause carries no argument at all, and a kind
    * whose arity does not match the shape below is one this table has no row for. Declining leaves
    * the duplicate parent and therefore the `E164` scalac already reports, which is the loud
    * direction. */
  private def carriesElement(k: Kind, kindParent: TypeRepr, shimParent: TypeRepr)(using p: Program): Boolean =
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

  /** WHICH classes with a RETAINED parent hold a value the target can be at a SLOT — K5.7's other
    * half, and the one that is not a refusal.
    *
    * [[restoreUninheritableParents]] keeps java's `Map.Entry` on a class implementing it, because
    * `Tuple2` cannot BE a parent. That says nothing about the class's VALUE meeting the `Tuple2`
    * slot every USE of the interface was retyped to (`Map.Entry<K,V> getEntry(int)`), and the
    * projection `(getKey, getValue)` there is a COPY — precisely what K2 refuses, because a later
    * `setValue` on the copy would succeed and write nothing.
    *
    * The refusal is about a CAPABILITY, and a class may have none. `MapEntry.setValue` is
    * `throw new UnsupportedOperationException()` in the library's own source, `Pair.setValue` throws
    * an `IllegalStateException`: java's own optional-operation refusal, written at the very member
    * the copy would detach. There is then no write-through to lose and the copy is EXACT — nothing
    * approximated, nothing guessed. Where the member really does write, the projection is
    * `CLAUDE.md` §4.4's silent defect and the seam stays the counted refusal it already is.
    *
    * Three things this asks, and each rules out a class that would be wrong to project:
    *
    *   - the class reaches a target in [[CollectionsTransform.UninheritableTargets]] through its
    *     parents, TRANSITIVELY, because the `implements` clause may sit on an interface the library
    *     declares — flexmark's `Pair implements Paired`, `Paired extends Map.Entry` — and a walk
    *     written for the shape in front of you is this file's own fast-path hazard (§4.56);
    *   - EVERY member the target cannot carry ([[CollectionsTransform.UnsupportedOnTarget]], by
    *     SIGNATURE — the same `MemberSig` test [[declaresUnimplementable]] reads for the opposite
    *     purpose) is answered by a body that throws before it does anything else. A class that
    *     declares NONE of them is abstract at that member and DECLINES, which is the conservative
    *     arm: an implementor could still write through;
    *   - the answering declaration is the NEAREST one, self then parents, so a subclass that
    *     RESTORES the write is not read through its refusing base.
    *
    * The EXCEPTION CLASS is deliberately not pinned. What licenses the projection is that no write
    * can happen, and a body whose first act is to throw cannot perform one whatever it throws —
    * `Pair` is the corpus's own evidence that a library picks a different exception for the same
    * contract.
    *
    * Read in [[run]] over the ORIGINAL units, which is not a convenience: this phase's own
    * [[refuseOnTarget]] substitutes a throw at exactly this member, so reading the mapped tree would
    * let the phase's refusal license its own projection — the port would then detach an entry whose
    * java writes through, and the only evidence would be a test. */
  private def detachedEntriesIn(p: Program): Map[SymId, String] =
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
  private def throwsFirst(t: Term): Boolean = t match
    case _: Tree.Throw     => true
    case b: Tree.Block     => b.stats.headOption match
      case Some(s: Term) => throwsFirst(s)
      case Some(_)       => false
      case scala.None    => throwsFirst(b.expr)
    case c: Tree.Commented => c.stmt match
      case s: Term => throwsFirst(s)
      case _       => false
    case _ => false

  /** THE VALUE'S OWN MINTED ANCESTRY, as a coercion source — K26's `DeclaredSubtype` half.
    *
    * `coerce` reads a source's kind out of `kindOf`, which is keyed on the phase's own SCALA TARGET
    * symbols, so it answers `None` for every type the PROGRAM declares — and that is exactly the
    * blindness `CollectionInternalCheck.Issue.DeclaredSubtype` was built to COUNT
    * (`ENGINE-LIMITS.md` K26): `OrderedSet implements java.util.Set` is emitted `extends
    * mutable.Set`, and handed to its own `retainAll(JavaCollection[?])` — java's own
    * `Set <: Collection`, an edge the mapping has no image for — no factory matched and the seam was
    * a bare `Found: … / Required: …`. The class really IS a `mutable.Set` here, because THIS PHASE
    * made it one, so `JavaCollection.fromSet` conforms and the seam closes at the slot the lane
    * already names.
    *
    * `None` where the value ALREADY CONFORMS, which is the conjunct that keeps this from wrapping
    * correct code: a class carrying the wanted shim among its parents needs nothing (and
    * `JavaCollection extends JavaIterable`, so it satisfies the iterable slot too). A class the
    * phase never re-parented is `None` as well, and its seam stays the honest compile error it was.
    *
    * WHICH kind, where a class carries two, is STATED rather than left to a `Set`'s iteration order
    * — which would publish a different emission per run for one fact, the one thing a baseline
    * cannot survive (`CLAUDE.md` §5). It is [[Kind]]'s own declaration order, `Seq` then `Map` then
    * `Set`. Java permits `implements List, Set` and no library in this corpus writes one; a wrong
    * pick there is a compile error naming the factory, never a silent wrap. */
  private def mintedSourceKind(head: SymId, wants: Option[SymId]): Option[Kind] =
    parentClash.get(head).filterNot { mp =>
      (wants.contains(javaIterableSym) &&
        (mp.shims(CollectionsTransform.JavaIterableFqn) || mp.shims(CollectionsTransform.JavaCollectionFqn))) ||
      (wants.contains(javaCollectionSym) && mp.shims(CollectionsTransform.JavaCollectionFqn)) ||
      (wants.contains(javaIteratorSym)   && mp.shims(CollectionsTransform.JavaIteratorFqn))
    }.flatMap(_.kinds.toList.sortBy(_.ordinal).headOption)

  /** PIN the call javac resolved, by ascribing the argument to the formal it resolved AT.
    *
    * `map.remove("0")` becomes `map.remove("0".asInstanceOf[java.lang.Object])` — java's own
    * spelling of the same disambiguation, `(Object) "0"`, and the node kind the frontend already
    * builds for one, so no emitter arm and no obligation row moves. The ascription makes java's
    * member UNIQUELY applicable: `java.lang.Object` conforms to the minted parent's `K`/`A` only
    * where that parameter IS `Object`, which is this function's one refusal.
    *
    * Every conjunct is the phase's own record (§4.56) and none is a name test:
    *
    *   - the callee's OWNER is a class [[declaredParentKinds]] says this phase re-parented;
    *   - the callee is a member THIS PROGRAM DECLARES, taking exactly one `java.lang.Object`. An
    *     inherited JDK member is [[inheritedKind]]'s business and has already declined by the time
    *     this is asked;
    *   - the minted parent declares that (name, arity) at its own type parameter
    *     ([[CollectionsTransform.ShadowedByTarget]]);
    *   - the ARGUMENT is not already an `Object`. Ascribing one to its own type changes nothing and
    *     would move emitted text on every port with such a call, which is the over-approximation
    *     `CLAUDE.md` §5 has no instrument for.
    *
    * REFUSED, and loudly rather than counted, where the minted parent's own KEY/ELEMENT type is
    * `java.lang.Object`: both alternatives then take an `Object` and no ascription can separate
    * them. That is read off the `extends` clause and NOT off the receiver — `class Any2Any
    * implements Map<Object, Object>` has no type parameter at all, so a receiver-only test sees a
    * bare `TypeRef`, finds no argument to look at and pins a call that is still ambiguous — and
    * where the clause writes one of the class's OWN parameters the receiver's instantiation is what
    * answers. The refusal leaves the `E051` exactly where it was, a compile error naming the two
    * alternatives, so it is the one shape in this family whose residue cannot be silent and §3's
    * count-the-refusal rule is met by the compiler rather than by a lane. */
  private def pinnedByObject(recv: Term, m: SymId, t: Tree.Apply)(using p: Program): Option[Term] =
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
    /** a `java.util.Stack`, which is a [[Seq]] PLUS five LIFO members whose names scala does not
      * have on a `Buffer` — and one of which, `peek`, means the opposite end from the `Deque`
      * `peek` the [[Seq]] arms already answer for. That collision is the whole reason this is its
      * own kind rather than another Seq entry: java's `Stack.peek()` is the LAST element and throws
      * when empty, java's `Deque.peek()` is the FIRST and returns null, and one arm cannot be both.
      *
      * Everything ELSE a stack can be sent is a `List` member (it extends `Vector`), so `rewrite`
      * falls back to [[Seq]] once the five have declined — see its own arm for why that is a
      * fallback rather than a second copy of the table. */
    case Stack
    /** a `java.util.Optional{Int,Long,Double}`, mapped to an `Option[…]` ALIAS. Not a collection at
      * all, and a kind for the one reason the others are: its member names differ — `getAsInt` is
      * `get`, `isPresent` is `isDefined`, `orElse` is `getOrElse` — and the arms are keyed on
      * `(name, args, kind)`. It shares no arm with any other kind, so nothing here can misfire on a
      * collection and nothing there on an `Option`. */
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

  /** the mapping targets that are STANDALONE — a `balticporter.runtime` type with java's own shape
    * and NO `scala.collection.*` parent, so nothing on the scala side is a subtype of one and one
    * is a subtype of nothing.
    *
    * This is the fact [[CollectionInternalCheck]] draws its whole line on, and it is emphatically
    * NOT "in the runtime package": three of this table's own targets live there and DO extend a
    * scala collection — `JavaStack extends mutable.ArrayBuffer`, `JavaEnumSet extends
    * mutable.AbstractSet`, `JavaEnumMap extends mutable.AbstractMap`, each said in its own doc and
    * each chosen precisely so java's subtype relation survives. A package test would call those
    * three unrelated to the scala family and report every correct slot they reach as a seam, which
    * is §4.56's name hazard met at a target rather than at a source.
    *
    * These are standalone because `CLAUDE.md` §4.5 says they must be: java's `Iterable`,
    * `Collection`, `Iterator` and `ListIterator` are small orthogonal interfaces a class implements
    * SEVERAL of, and scala's collection traits are large and interlocking, so modelling them on one
    * is illegal for the shape every collection library has. The price is exactly this lane. */
  val standaloneTargets: Set[String] =
    Set(JavaIterableFqn, JavaCollectionFqn, JavaIteratorFqn, JavaListIteratorFqn)

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

  /** the class file formal a COLLAPSED stream chain has to become a `Stream` again for.
    *
    * The collapse (K6) rewrites `xs.stream().map(f)` to `xs.map(f)`, which is right wherever the
    * chain's TERMINAL is inside the program — `collect` materialises, so the observable result is
    * the same. Where the chain is not terminated, its value crosses back out to java at a `Stream`
    * slot, and the collapsed `Buffer` is then handed to a formal no `toJava` overload serves:
    * `Found: Buffer[LNode] / Required: Stream[? <: LNode]` at `Stream.concat`. Naming the JDK type
    * here is a fact ABOUT THE JDK, exactly as `typeMap`'s own keys are, and not §4.56's
    * decide-from-a-prefix (nothing is concluded about a type from its NAME; an exact FQN is
    * compared against an exact FQN). */
  private[balticporter] val StreamFqn = "java.util.stream.Stream"

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
  /** the shims as FQNs — one derivation, read by the instance's own `shimFqns` and by the specs
    * that ask whether a `typeMap` TARGET is one. A shim carries java's own names and arity and is
    * skipped by `rewrite`'s blanket guard before any arm, so a member "handled at its kind" is NOT
    * handled at a shim receiver — which is the difference a refusal keyed at such an owner is
    * about, and which a second copy of this set could silently stop agreeing with (F8).
    *
    * It must stay BELOW the `*Fqn` vals, for the reason `typeMap` states at length. */
  private[balticporter] val ShimFqns: Set[String] =
    Set(JavaIterableFqn, JavaIteratorFqn, JavaListIteratorFqn, JavaCollectionFqn)

  private[balticporter] val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // a java `LinkedList` is a List AND a Deque, and the corpus uses it as a QUEUE.
    // `mutable.Queue` extends `ArrayDeque` extends `Buffer`, so every Seq rewrite above still
    // applies and `removeHeadOption` exists — which `ListBuffer` does not have.
    "java.util.LinkedList"    -> ("scala.collection.mutable.Queue", Kind.Seq),
    // `Vector` and `Stack` are java's two LEGACY sequences, and both are ABSENT from Scala.js —
    // `Stack` from Scala Native too — so a port that leaves them alone compiles on the JVM and does
    // not link anywhere else. Neither had a corpus site when the mapping was written except
    // `Stack`, which has one.
    //
    // `Vector` is an `ArrayBuffer` for `ArrayList`'s reasons and preserves the relation java
    // declares (`Vector implements List`, and `ArrayBuffer <: Buffer`). What it does NOT preserve is
    // the `synchronized` on every method — a fact about the JDK type that no scala collection has
    // and that a port relying on it would lose silently. It is stated here and in the platform
    // survey rather than approximated, because the alternative (wrapping every access) is a
    // performance decision this engine has no standing to take on a caller's behalf. Vector's
    // ENUMERATION-era members (`addElement`, `elementAt`, `setElementAt`, `elements`) have no arm:
    // they reach `jdk-surface` as a hole, which is the loud answer, and adding an arm apiece is one
    // line each the day a port has one.
    "java.util.Vector"        -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // `Stack` is a SHIM, and NOT `scala.collection.mutable.Stack` — the target the platform survey
    // recommended, and which this mapping deliberately does not take. Java's
    // `Stack extends Vector extends List`: its top is its LAST element, `get(0)` is the bottom, and
    // its iterator runs bottom-to-top. Scala's `mutable.Stack` is an `ArrayDeque` whose `push`
    // PREPENDS, so its top is element 0 and it iterates top-to-bottom. The two agree on
    // `push`/`pop`/`peek` in isolation and disagree on every LIST-shaped read of the same object,
    // with no compile error anywhere — CLAUDE.md §4.4's shape at a type, and precisely the failure
    // mode the survey names ("an AVAILABILITY gap reproduced without the SEMANTIC guarantee").
    //
    // Nor is it `ArrayBuffer`, which was built first and cannot work: [[kindOf]] is keyed on the
    // TARGET, so two java types sharing one map to the same rewrites, and `java.util.ArrayList` is
    // already there. A `Stack` receiver would then be a receiver the phase cannot tell from a list
    // — and `peek()` at `Kind.Seq` is the DEQUE `peek`, the FIRST element and `null` when empty.
    //
    // Its own type, so java's five can simply BE members with java's names and java's contracts;
    // `JavaStack extends ArrayBuffer` is what keeps `Stack <: Vector <: List` on the scala side.
    // See [[Kind.Stack]] for the one member that cannot be java's.
    "java.util.Stack"         -> (JavaStackFqn, Kind.Stack),
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
    // …and its BIDIRECTIONAL sibling, for the rule the `Queue`/`Deque` and `ConcurrentHashMap`
    // blocks state three times over: A MAPPING MUST PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE
    // RELATIONS. `java.util.ListIterator extends java.util.Iterator`, so mapping the parent and not
    // this one splits an edge every `Iterator`-typed slot depends on — which is not a hypothesis:
    // `collection-closure` was already reporting exactly that, twice, on the one corpus port that
    // names the type. `JavaListIterator extends JavaIterator`, so the edge survives.
    //
    // NOT refused with `spliterator` beside it, though K23 counted the two together. That refusal
    // is about `scala.collection.Iterator` and the RECEIVER here is a `mutable.Buffer`, which has
    // indexed read, indexed update, insert and remove — `ListIterator`'s whole contract, written
    // THROUGH to the caller's own list (§4.5's standalone shim, `JavaListIterator.over`).
    "java.util.ListIterator"  -> (JavaListIteratorFqn, Kind.Seq),
    "java.util.Map"           -> ("scala.collection.mutable.Map", Kind.Map),
    "java.util.HashMap"       -> ("scala.collection.mutable.HashMap", Kind.Map),
    "java.util.LinkedHashMap" -> ("scala.collection.mutable.LinkedHashMap", Kind.Map),
    "java.util.TreeMap"       -> ("scala.collection.mutable.TreeMap", Kind.Map),
    // `ConcurrentHashMap` is a `java.util.Map`, so a port that mapped the interface and not this
    // one splits the relation the source depends on — `Map<String, Object> m = new
    // ConcurrentHashMap<>()` reads `Found: ConcurrentHashMap / Required: mutable.Map` and there is
    // no seam at a `new`. It is the same rule the `Queue`/`Deque` block states, met at the
    // concurrent package: A MAPPING MUST PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE RELATIONS.
    //
    // `scala.collection.concurrent.TrieMap` and not `mutable.HashMap`, because the concurrency is
    // the whole reason the java names this type: `TrieMap` is scala's lock-free concurrent map and
    // is a `mutable.Map`, so every rewrite above still applies. Downgrading to `HashMap` would
    // compile and lose thread-safety silently, which is §4.4's shape at a type rather than at a
    // statement. What is NOT reproduced is `ConcurrentMap`'s atomic `putIfAbsent`/`replace`
    // contract; `TrieMap` has both and they are atomic there too.
    "java.util.concurrent.ConcurrentHashMap" -> ("scala.collection.concurrent.TrieMap", Kind.Map),
    "java.util.concurrent.ConcurrentMap"     -> ("scala.collection.concurrent.Map", Kind.Map),
    // a scala `Map` IS an `Iterable[(K, V)]`, so java's `Map.Entry` — a key/value pair with no
    // identity of its own — is a `Tuple2`. `getKey`/`getValue` become `_1`/`_2` (below).
    // Spoon's qualified name for a nested type separates with `$` — that is the key that fires;
    // the dotted spelling is an alias for frontends that name nested types with `.`.
    "java.util.Map$Entry"     -> ("scala.Tuple2", Kind.Entry),
    "java.util.Map.Entry"     -> ("scala.Tuple2", Kind.Entry),
    // `EnumMap`/`EnumSet` are SHIMS and not mappings, which is the one place this table says
    // "no stdlib type will do" about a SEMANTIC rather than about a shape. Both are absent from
    // BOTH non-JVM backends, so keeping the JDK type is a link error there — and both GUARANTEE
    // iteration in the enum's declaration (ordinal) order, which a `HashMap` does not have and a
    // `LinkedHashMap` answers with INSERTION order instead. Mapping onto either would reproduce the
    // AVAILABILITY and silently drop the GUARANTEE, which is catalog row `JS-C42` and CLAUDE.md
    // §4.4's defect class reached through a type mapping. The shims keep java's order; see their
    // own docs for how, and `JavaEnumSet`'s companion for why its statics carry the class token
    // (java's `allOf`/`range`/`complementOf` need the CONSTANTS, and that is where they come from).
    "java.util.EnumMap"       -> (JavaEnumMapFqn, Kind.Map),
    "java.util.EnumSet"       -> (JavaEnumSetFqn, Kind.Set),
    // …and the three PRIMITIVE optionals, absent from Scala.js and present on Native, which is a
    // disagreement one emitted program cannot straddle. The targets are type ALIASES rather than
    // `scala.Option` itself because the retype is ARITY-CHANGING: this phase moves a type by
    // replacing the head symbol and carrying the arguments across, `OptionalInt` has none and
    // `Option` takes one, so the head swap alone would emit `scala.Option` un-applied at every
    // occurrence. An alias is that type with the argument already supplied — nothing wrapped and
    // nothing copied. `java.util.Optional` itself is deliberately absent: it is present on all
    // three backends, so mapping it would be a shape preference rather than a portability need.
    "java.util.OptionalInt"    -> (JavaOptionalIntFqn, Kind.Opt),
    "java.util.OptionalLong"   -> (JavaOptionalLongFqn, Kind.Opt),
    "java.util.OptionalDouble" -> (JavaOptionalDoubleFqn, Kind.Opt),
    "java.util.Set"           -> ("scala.collection.mutable.Set", Kind.Set),
    // …and its ABSTRACT BASE, for the reason `Collection`/`AbstractCollection` above gives and as
    // the FOURTH instance of one rule: A MAPPING MUST PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE
    // RELATIONS. `java.util.AbstractSet implements java.util.Set`, so an entry for the interface and
    // none for the base sends java's own pair to unrelated types. Unlike the other three this one
    // was REPORTED rather than merely suffered — `collection-closure` filed it as a lost JDK
    // relation for as long as it stood — and it is worth saying which instrument saw it, because
    // the other three arrived as compile errors somebody had to diagnose.
    //
    // ==THE TARGET IS A TWO-WAY BIND, and this entry could not be written until the phase answered
    // it (`ENGINE-LIMITS.md` K29)==
    // `mutable.Set` keeps `AbstractSet <: Set` and DROPS the four JDK defaults a class that defines
    // a set calls through `super`. The SHIM keeps those defaults — it carries java's own member
    // names, which is exactly why `AbstractCollection` never had this problem — and BREAKS the
    // subtype edge, which is the 13-error split the `Collection`/`AbstractCollection` block already
    // measured from the other side. Neither target is right on its own, so there is no row anybody
    // could have got right: the mapping is `mutable.Set` and the defaults are the PHASE's to supply.
    // See [[CollectionsTransform.VirtualJdkDefaults]], which is what makes this line free.
    "java.util.AbstractSet"   -> ("scala.collection.mutable.Set", Kind.Set),
    "java.util.HashSet"       -> ("scala.collection.mutable.HashSet", Kind.Set),
    "java.util.LinkedHashSet" -> ("scala.collection.mutable.LinkedHashSet", Kind.Set),
    "java.util.TreeSet"       -> ("scala.collection.mutable.TreeSet", Kind.Set),
  )

  /** [[typeMap]] as ONE stable string — the half of [[CollectionsTransform.surfaceFingerprint]]
    * that belongs to the ENGINE rather than to an instance.
    *
    * Sorted by java FQN and carrying both the target and the kind, because both decide emitted
    * text: a key that moved from `Kind.Seq` to `Kind.Stack` changes what `peek()` becomes without
    * changing one type name. Declared here beside the table so a new entry cannot be added without
    * passing it. */
  private[transform] def mappingDigest: String =
    balticporter.tir.TirPrinter.sha256(
      typeMap.toList.map((k, v) => s"$k->${v._1}:${v._2}").sorted.mkString(",")).take(16)

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

  /** The mapping TARGETS at which a REIFIED occurrence — an `instanceof`, a downcast — can be
    * translated, and the `JavaCollections.Reified` member that does it.
    *
    * A retyping moves STATIC types. An `instanceof` and a cast are questions asked of a RUNTIME
    * OBJECT, and the retyping moved neither the objects nor their classes, so translating one by
    * moving its type alone changes the ANSWER — valid Scala meaning something else, which is
    * CLAUDE.md §4.4's defect class arriving through a retype (`ENGINE-LIMITS.md` K18). The runtime
    * members named here answer java's question over BOTH representations a port legitimately holds
    * at an `Object` slot: the ones its own code made, and the ones an external producer made.
    *
    * ==Why this is not [[liveWrappable]], one entry over==
    * The two tables answer different questions and the difference is exactly `JavaCollection`.
    * `liveWrappable` asks *can a wrap be emitted toward this target from a DECLARED type* — and for
    * the shim the answer is no, because `java.util.Collection` has no `scala.jdk` converter and a
    * wrapper over a copied `Buffer` would detach both directions. Here the OBJECT is in hand: there
    * is no overload to resolve and no element type to guess, so `JavaCollection.fromJava` delegates
    * to java's own collection and nothing is copied.
    *
    * ==and why the CONCRETE targets are absent==
    * `mutable.HashMap`, `ArrayBuffer`, `ArrayDeque`, `TrieMap`, `Tuple2`: no live view can BE one of
    * these, so a cast to one is a boundary with no coercion behind it. That is refused and COUNTED
    * (`CollectionBoundaryCheck.Issue.ReifiedOccurrence`), never approximated (M6). */
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

  /** THE JDK TYPES A RETYPED VALUE STOPS BEING, and that this phase never sees a name for.
    *
    * A `java.util.ArrayList` is a `RandomAccess`, a `Cloneable`, a `java.io.Serializable`, an
    * `AbstractList` and (since 21) a `SequencedCollection`. An `ArrayBuffer` is none of them. So
    * `x instanceof RandomAccess` over a value this phase retyped answers NO where java answered
    * YES — and the occurrence names NOTHING this phase moved, so every reified arm declines and the
    * node is emitted verbatim. That is the same defect as `ENGINE-LIMITS.md` K18 at a target the
    * mapping does not own, and it has one less instrument on it: a mapped-but-concrete target at
    * least reaches the refusal count, and this one reached nothing.
    *
    * DERIVED, never listed: the supertype/interface closure of [[typeMap]]'s own java keys, minus
    * those keys. A list would go stale the day the JDK adds an interface (`SequencedCollection` is
    * exactly that day) or the day a key is added here, and it would be a name test besides — which
    * §4.56 forbids for the reason libGDX demonstrates: `com.badlogic.gdx.utils.Json$Serializable`
    * is a `Serializable` by simple name and shares nothing with `java.io.Serializable`. The
    * separator is the JVM's own (`java.util.Map$Entry`), which is `Symbol.fullName`'s too.
    *
    * `java.lang.Object` is excluded because it is in EVERY closure and is never a divergence:
    * everything is an `Object` in scala too. A key the running JDK cannot load — the dotted
    * `java.util.Map.Entry` alias — contributes nothing, which is right: it is an alias for a key
    * that loads. */
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

  /** REIFIED CARRIERS java itself guarantees — the §1(a) half of `reifiedCarriers` (K20).
    *
    * `java.lang.Class<T>` is the only one. Its argument is not a claim about a slot: it names the
    * class the JVM will be asked for, so `readValue(json, HashMap.class)` retyped to
    * `classOf[mutable.HashMap]` asks a framework for a class file that does not exist. That is true
    * of every codebase and takes no configuration, which is what puts it in this file beside
    * [[typeMap]] rather than in a manifest. Every other carrier — jackson's `TypeReference`, Gson's
    * `TypeToken`, Guice's `Key`/`TypeLiteral` — is a fact about a library's DEPENDENCIES and is the
    * constructor parameter.
    *
    * Note this is only reached where the argument is a TYPE. A `classOf[…]` LITERAL carries its type
    * in `Constant.ClassOfC`, which `StandardTraversal.mapTerm` does not map at all, so a class
    * literal has always been preserved — by omission rather than by decision, and the omission is
    * recorded in `ENGINE-LIMITS.md` K20 because it is the reason the corpus never measured this
    * defect at a literal. What this entry covers is the DECLARED slot: a field or a parameter typed
    * `Class<Map<String,Object>>`, which the traversal does reach. */
  private[balticporter] val UniversalCarriers: Set[String] = Set("java.lang.Class")

  /** …and the members that RETAINED PARENT declares which its mapping target cannot carry.
    *
    * Keeping java's parent (`restoreUninheritableParents`) makes the class compile as far as its
    * PARENT goes and leaves the other half open: the class must still IMPLEMENT everything that
    * parent declares, and the member whose body was written against the java type is exactly the
    * one the target has no counterpart for. `java.util.Map.Entry#setValue` is that member and is
    * the whole of this table — `getKey`/`getValue` are `_1`/`_2` and translate.
    *
    * Read the values as *what the target cannot express*, not as *what the phase gave up on*: java
    * declares `setValue` an OPTIONAL operation whose contract is `UnsupportedOperationException`
    * where the backing map does not support the write, so a ported entry that has no reachable map
    * throwing it is a CONFORMING `Map.Entry` rather than a hole. Derived from the phase's own
    * mapping (a key here is a target in [[UninheritableTargets]]), never from a receiver's name. */
  private[balticporter] val UnsupportedOnTarget: Map[String, Set[MemberSig]] =
    Map("scala.Tuple2" -> Set(MemberSig("setValue", 1)))

  /** ONE MEMBER OF AN INTERFACE, by the shape java resolved it at — never by its bare name.
    *
    * `java.util.Map.Entry` declares exactly `setValue(V)`, and a class implementing it may declare
    * any number of unrelated methods that happen to share those five letters. Java resolves them
    * separately and the interface says nothing about them, so a bare-name match refused a
    * `setValue(int, int)` with a perfectly good body — a member replaced by a throw for a name
    * collision, with a green compile and no count moving (CLAUDE.md §3).
    *
    * `arity` is the whole of the discrimination available here and it is enough: the declaring
    * interface is EXTERNAL, so the frontend interned it with no `Definition` and no member list
    * (§4.56), and the parameter type it declares is a type VARIABLE, which erases to `Object` and
    * so distinguishes nothing anyway. An overload agreeing on name AND arity still has to pass the
    * second, stronger gate — that the phase can point at what it broke — before anything is
    * substituted. */
  private[balticporter] final case class MemberSig(name: String, arity: Int)

  /** …and the members a MINTED PARENT declares AT ITS OWN KEY/ELEMENT TYPE which java declares over
    * `Object`, keyed by the [[Kind]] of the mapped java parent that produced it.
    *
    * ==What this table is FOR==
    * Re-parenting a class the PROGRAM declares — `OrderedMap implements java.util.Map` emitted
    * `extends scala.collection.mutable.Map` — adds MEMBERS, which is exactly what `CLAUDE.md` §4.5
    * says a parent does and an extension does not. Java's candidate set for `map.remove("0")` was
    * ONE member (the class's own `remove(Object)`, java's by-value lookup); scala's is TWO, because
    * the minted parent contributes `remove(key: K): Option[V]` and a `String` argument matches both.
    * `E051 Ambiguous overload` — a compile error the ENGINE'S OWN TRANSLATION created, at a call
    * java resolved unambiguously and at a member the port must go on declaring.
    *
    * ==Why a TABLE and not a derivation==
    * The far side of the clash is a scala TRAIT this run never parsed, so there is no `Definition`
    * to read its members off and `OverrideGraph` has no node for it — the same reason
    * [[UnsupportedOnTarget]] and `loadFactorSyms` are lists. What keeps it out of §4.56's
    * name-hazard is that every key is a `Kind` of the phase's OWN [[typeMap]] and every value is a
    * member of the target THAT ENTRY names: nothing is concluded from a receiver's spelling.
    *
    * ==Read it as "declared at the type parameter", which is what makes the pin work==
    * Each member below takes the collection's `K`/`A`, so ascribing the argument to
    * `java.lang.Object` makes java's overload the UNIQUELY applicable one — `Object` conforms to a
    * type parameter only where that parameter IS `Object`, which is [[pinnedByObject]]'s one
    * refusal. A member scala declares over something else (`Buffer.remove(Int, Int)`) is not a
    * clash and is deliberately absent: the pin would move emitted text for nothing.
    */
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

  // -------------------------------------------------------------------------------------------
  // WHAT A MINTED PARENT ACTUALLY OVERRIDES — `ENGINE-LIMITS.md` K28
  // -------------------------------------------------------------------------------------------
  //
  // The frontend puts `override` on a member because JAVA's hierarchy justified it
  // (`SpoonTir.overridesInherited`). Re-parenting the class moves the far side of that statement,
  // and the modifier is then a claim about a type the emitted class does not extend: scalac reads
  // `E037 overrides nothing` or `E038 different signature` at a member whose name, formals and body
  // are all correct. 73 of ssg-md's 131 `RefChecks` rows are exactly this.
  //
  // WHY A TABLE AND NOT A DERIVATION — `ShadowedByTarget`'s argument, verbatim and for the same
  // reason: the far side is a scala TRAIT this run never parsed, so there is no `Definition` to read
  // its members off and `OverrideGraph` has no node for it. Reflection over `scala-library` is not
  // the missing derivation either, and the reason is measured rather than aesthetic: a JVM method
  // list answers NAME AND ERASED PARAMETERS, and every `E038` row in that census is a member whose
  // erasure MATCHES scala's (`contains(Object)` against `contains(A)`, `addAll(Collection)` against
  // `addAll(IterableOnce)`) and whose SOURCE-level signature does not. An erasure-keyed answer would
  // keep the modifier on all 24 of them.
  //
  // ==Read it as the KEEP list, and note that BOTH of its errors are LOUD — WITH ONE EXCEPTION==
  // A member listed here keeps `override`; everything else on a re-parented class loses it. Too
  // SMALL a list strips a modifier scala requires and scalac says `needs "override" modifier`; too
  // LARGE a list leaves the `E037` exactly where it was. That is what licenses a table here at all —
  // contrast `ExternalSurface`, whose unknown side is a rename that no compiler can see, and which
  // therefore has to anchor on absence.
  //
  // ==THE EXCEPTION IS AN ABSTRACT PARENT DECLARATION, AND IT IS SILENT IN THE `TOO SMALL`
  // DIRECTION== Scala makes `override` OPTIONAL on the implementation of an abstract member, so a
  // strip whose parent declaration is abstract produces no error at all: the member is entitled to
  // the modifier, loses it, and nothing anywhere says so. Six members did exactly that, at 0 errors
  // and 0 moved check counts, and the only instrument that saw them was `members.tsv` (`CLAUDE.md`
  // §4.55's minted-member paragraph). **The arity reading is the instrument, and it is a HAND
  // reading**: strips performed against error rows closed was **77 against 71**, and the residue is
  // the population to explain — six abstract-parent strips, and nothing else. Read the two numbers
  // against each other whenever this table moves; a strip with no error row behind it is what an
  // over-approximation looks like from here.
  //
  // It is NOT a gate, and the reason is where the second number lives. `strips performed` is a fact
  // this run holds; `error rows closed` is a DELTA between two compiles of two commits, joined
  // through `errors.tsv` by the correlator, and no run can assert on a number it cannot see. An
  // assertion written from one side alone would be an assertion about nothing.
  //
  // A target with NO entry keeps every modifier: unknown is the conservative arm, exactly as
  // `ExternalSurface.mayDeclare` reads it.
  //
  // Three families of member are deliberately ABSENT from every row and none of them is an
  // oversight: `equals`/`hashCode`/`toString`/`clone` are `java.lang.Object`'s, which is above every
  // java type whether or not the tree says so and which `strippedOverrides` asks separately
  // (`ExternalSurface.javaLangObjectDeclares`); a member the PROGRAM's own hierarchy declares is
  // `OverrideGraph.overridden`, asked separately again; and a member that is here and STILL wrong is
  // an `E164`, which no modifier fixes and which is the family's other half.

  /** WHICH (name, arity) a java member emitted at this KIND's target really overrides.
    *
    * Keyed by [[Kind]] rather than by target FQN because the answer is the same for every target of
    * one kind: `mutable.HashMap`, `LinkedHashMap`, `TreeMap` and `concurrent.TrieMap` all inherit
    * `MapOps`, and the members below are `MapOps`'s. That is the same key `ShadowedByTarget` uses,
    * and for the same reason.
    *
    * `Kind.Entry` and `Kind.Opt` have no row: `Tuple2` is in [[UninheritableTargets]] so no class is
    * ever emitted under it, and an `Option` alias cannot be a parent either. */
  private[balticporter] val OverridesTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // `mutable.Map` — the four that simply agree, the two views, and the three that reach scala's
    // member and DISAGREE about the result (`E164`, which keeping the modifier is what reports).
    // `get`/`remove`/`containsKey`/`containsValue`/`entrySet`/`putAll`/`forEach` are all absent:
    // the first two take `K` where java takes `Object` (`ShadowedByTarget`'s own row, read here for
    // the opposite purpose) and the last five have no scala counterpart at any arity.
    Kind.Map.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("keySet", 0),
      ExternalSurface.Member("keys", 0), ExternalSurface.Member("values", 0),
      ExternalSurface.Member("iterator", 0), ExternalSurface.Member("put", 2),
    ),
    // `mutable.Set` — `add(A)` is the one java member whose scala counterpart takes the ELEMENT and
    // therefore matches java's own `add(E)`. `remove`/`contains` take `A` against java's `Object`;
    // `addAll` takes an `IterableOnce` against java's `Collection`; `containsAll`/`removeAll`/
    // `retainAll`/`toArray` have no counterpart at all.
    Kind.Set.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("iterator", 0),
    ),
    // `mutable.Buffer` — and the one row in this table that needs a DESCRIPTOR rather than an arity,
    // which is the reason `ExternalSurface.Member` is the entry type here. `remove` is declared
    // TWICE on a java `List` (`remove(int)` by index, `remove(Object)` by value) and scala's
    // `Buffer` has only the first; keyed on `("remove", 1)` alone the table would keep the modifier
    // on both and leave java's by-value overload reading `E038`. Java's own spelling on both sides —
    // `Symbol.descriptor` is the parser's, taken before any retyping (`Descriptor`'s own note).
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

  /** …and the same question at a STANDALONE target, where the answer is EXACT BY CONSTRUCTION.
    *
    * These four traits are the ENGINE'S OWN (`balticporter/runtime`), written to java's shape and
    * java's arity precisely so that a ported `override def iterator()` keeps meaning what it meant
    * (§4.5). So this is not a claim about a library the engine cannot see: it is the engine quoting
    * itself, and an absence here really is proof — `JavaIterable` declares `iterator()` and NOTHING
    * ELSE, which is why every `forEach`/`spliterator`/`forEachRemaining` in the census is an `E037`.
    *
    * A table rather than a `dependsOn(runtime)`: `build.sbt` keeps the engine off the runtime on
    * purpose, so that two ports of two modules of one library share ONE `JavaIterator` rather than
    * each vendoring a copy at the same FQN. `RuntimeSurfaceSpec` is what holds the two in step. */
  /** WHICH standalone shim a minted KIND target already SUBSUMES — `ENGINE-LIMITS.md` K28.1.
    *
    * Java's `Map` and `Iterable` are independent interfaces and a class implements both; java
    * relates them at ONE MEMBER spelled two ways (`Map` has `entrySet().iterator()`, `Iterable` has
    * `iterator()`, and the class writes the second). Minted onto `scala.collection.mutable.Map` AND
    * onto [[JavaIterableFqn]] the emitted class declares `iterator(): JavaIterator[…]` under a
    * parent that declares `iterator: Iterator[…]`, which is `CLAUDE.md` §4.5's sentence arriving at
    * a MINTING rather than at a shim's design: no repair at the member can help, because the
    * conflict is in the parents.
    *
    * The answer is K29's rule read at a DUPLICATE rather than at a missing edge — *a mapping must
    * preserve the source library's own subtype relations* — and `mutable.Map <: Iterable` already
    * carries the relation the second `implements` clause was there for. Minting both states one
    * relation at two arities.
    *
    * ==Why exactly one shim is on the right-hand side==
    * A row here is a claim that the KIND's target is a supertype answering for the shim's WHOLE
    * surface, and [[OverridesShim]] is the surface: `JavaIterable` declares `iterator()` and
    * nothing else, so an `Iterable`-derived target answers all of it. `JavaCollection` declares
    * fourteen members and NO scala collection is a subtype of it (that is what
    * [[standaloneTargets]] means), so dropping it would be an immediate `Not Found` at every one of
    * them; `JavaIterator`/`JavaListIterator` are not supertypes of any of these targets either.
    * Both errors are loud in the [[OverridesTarget]] sense — too few rows leaves the `E164` this
    * closes, too many leaves a `Not Found` naming the member — which is what licenses a table.
    *
    * Keyed by [[Kind]] for [[OverridesTarget]]'s reason: every target of one kind inherits the same
    * `scala.collection` trait, and it is that trait — not the concrete target — that carries the
    * relation. `Kind.Entry`/`Kind.Opt` have no row because no class is emitted under either
    * ([[UninheritableTargets]], and an `Option` alias cannot be a parent). */
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

  // -------------------------------------------------------------------------------------------
  // …and the SURFACE the minted parent DECLARES that java's own member cannot be — K28.1
  // -------------------------------------------------------------------------------------------
  //
  // [[OverridesTarget]] above answers *is this modifier still true*; this table answers the family
  // that no modifier repairs. A class re-parented onto `scala.collection.mutable.{Map, Set, Buffer}`
  // owes that trait's abstract surface, and the java members it declares are the wrong SHAPE for it
  // twice over: `put(K,V): V` against `Option[V]`, `iterator(): JavaIterator[A]` against a
  // parameterless `Iterator[A]`, and `size(): Int` against a member that is `final` on a `SeqOps`.
  //
  // ==Why RENAME + SYNTHESISE rather than retype java's member==
  // *Scala's member wins* — retype `iterator()` to `Iterator[A]` and be done — closes the row and
  // opens the ones its RESULT TYPE was carrying: three declarations in one corpus library return
  // `ReversibleIndexedIterator<X>` with `return anOrderedSet.iterator()` as their whole body, and
  // that capability leaves the emitted surface with the retyping. Renaming java's member to
  // `<name>$java` moves a NAME and nothing else — §4.55's machinery re-points every reference
  // exactly, because java resolved all of them statically — and the synthesised scala member over it
  // is what the parent asked for. The emitted surface at the java name is then what the retyping
  // would have produced, with java's own member surviving beside it rather than deleted.
  //
  // ==Why the delegate is RENAMED even where scalac would accept the pair==
  // The synthesised body has to NAME the java member, and scala resolves `this.get(k)` at `k: K`
  // to the inherited `MapOps.get(K): Option[V]` — which calls nothing but itself. A bridge that
  // recursed would compile, move no count and stack-overflow at the first call: `CLAUDE.md` §4.4's
  // defect class reached through a delegation. So the delegate is renamed whenever it is a
  // delegate, and the body then names a member nothing else declares.
  //
  // ==Both error directions are LOUD, which is what licenses a table here==
  // Exactly [[OverridesTarget]]'s argument: a row too few leaves the `E164`/`needs to be abstract`
  // the compiler already states, a row too many renames a java member and synthesises a scala one
  // that overrides nothing — `E037`. Neither is silent. What is NOT in the table is any member a
  // java class could implement AS WRITTEN (`size()` on a `Map`, `isEmpty()`, `clear()`, `add(E)` on
  // a `Set`), because a bridge there would move emitted surface for nothing.

  /** ONE member of that surface: what SCALA declares, and the JAVA member the bridge delegates to.
    *
    * `from` is a preference list and not one entry, because java relates its own members and a class
    * need not declare the nearest: a `Map` implementor has no `iterator()` at all unless it also
    * implements `Iterable`, and java's own spelling for the same traversal is
    * `entrySet().iterator()`. The FIRST entry the class declares wins; where none is declared the
    * row does not fire.
    *
    * `from = Nil` is the third case and means the row fires unconditionally with NO delegate — the
    * three `mutable.Buffer` members java's `List` has no counterpart for at all, whose bodies are
    * `JavaCollections`' own (see there). A row with a delegate is a translation of java; a row
    * without one is scala's contract met over the bridges beside it, and the two are kept apart
    * here rather than in the builder so a reader can see which is which. */
  private[balticporter] final case class Bridged(scalaName: String, arity: Int,
                                                 from: List[ExternalSurface.Member],
                                                 required: Boolean = true)

  private val ObjectArg = Some(Descriptor(List(Param.Named("Object"))))
  private val IntArg    = Some(Descriptor(List(Param.Prim("int"))))

  /** keyed by [[Kind]] for [[OverridesTarget]]'s reason: `mutable.HashMap`, `LinkedHashMap`,
    * `TreeMap` and `TrieMap` all inherit one `MapOps`, and it is that trait's surface this is.
    *
    * `Kind.Stack`'s target is a CONCRETE runtime class with nothing abstract on it, and
    * `Kind.Entry`/`Kind.Opt` are never a parent ([[UninheritableTargets]]) — so all three have no
    * row and the mechanism is a no-op for them by arithmetic. */
  private[balticporter] val BridgedTarget: Map[String, List[Bridged]] = Map(
    Kind.Map.toString -> List(
      // `MapOps.put` is CONCRETE and returns `Option[V]`; java's returns the value or null.
      Bridged("put",         2, List(ExternalSurface.Member("put", 2)), required = false),
      // `MapOps.get` is ABSTRACT and takes `K`; java's takes `Object` ON PURPOSE (K24), so the two
      // are a legal overload pair — measured — and the recursion above is the reason for the rename.
      Bridged("get",         1, List(ExternalSurface.Member("get", 1, ObjectArg))),
      // `Growable.addOne` / `Shrinkable.subtractOne`, both abstract, both spelled over java's own
      // `put`/`remove`. No java member is named either of these, so nothing is renamed FOR them —
      // they ride on the two rows above.
      Bridged("addOne",      1, List(ExternalSurface.Member("put", 2))),
      Bridged("subtractOne", 1, List(ExternalSurface.Member("remove", 1, ObjectArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0),
                                  ExternalSurface.Member("entrySet", 0))),
      // …and the two `MapOps` declares CONCRETELY, so a class that declares neither owes nothing
      // and is not reported as owing anything. Only a java member of the SAME NAME is a problem,
      // which is exactly when the row fires.
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
      // java declares BOTH `remove(int)` and `remove(Object)` on a `List` and scala's `Buffer` has
      // only the first — the descriptor is what keeps the by-value overload out of this row, which
      // is the same distinction `OverridesTarget`'s `Seq` row carries for the same pair.
      Bridged("remove",      1, List(ExternalSurface.Member("remove", 1, IntArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0))),
      // `SeqOps.contains[A1 >: A](elem: A1)` is CONCRETE, so a class that declares no `contains` of
      // its own owes nothing — and one that does declare java's `contains(Object)` has a member
      // with the SAME ERASURE as scala's, which is `E120 Name clash` rather than an override. It is
      // the `Kind.Map` `values`/`keys` shape at a different trait, and the only row whose scala
      // side is GENERIC: the bridge has to carry `[A1 >: A]` or it is a second overload at the same
      // erasure and the clash is exactly where it was.
      Bridged("contains",    1, List(ExternalSurface.Member("contains", 1, ObjectArg)), required = false),
      Bridged("indexOf",     1, List(ExternalSurface.Member("indexOf", 1, ObjectArg)),  required = false),
      // …and the three with no java counterpart at all. See `JavaCollections`' own note.
      Bridged("remove",       2, Nil),
      Bridged("insertAll",    2, Nil),
      Bridged("patchInPlace", 3, Nil),
    ),
  )

  /** WHICH delegates the emitted parent would CAPTURE — the set that decides the rename, and the
    * only reason [[BridgedTarget]] is not the whole mechanism.
    *
    * A bridge body names its delegate, and scala resolves that name against the class AND its new
    * parent. Where the parent declares the same (name, arity), the call binds to the PARENT's member
    * — `this.get(k)` at `k: K` is `MapOps.get(K): Option[V]`, which is the bridge itself — and the
    * emitted body is an infinite recursion that compiles, moves no count and overflows the stack at
    * the first call. So a captured delegate is renamed out of the way and an uncaptured one is left
    * exactly as java wrote it.
    *
    * Both directions are loud, again: a missing entry is that recursion, and a spurious one renames
    * a java member the parent never declared and synthesises a scala member over it that overrides
    * nothing (`E037`). What is deliberately ABSENT is every delegate the target does not declare —
    * `entrySet()`, and a `Buffer`'s `get(int)`/`set(int,E)`/`add(int,E)`/`add(E)`, none of which is
    * a `scala.collection` member name — because renaming those would move emitted surface for a
    * hazard that does not exist. */
  private[balticporter] val CapturedByTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // `MapOps` declares all six, three of them concretely (`put`, `values`, `keys`) and `get`
    // abstractly; `iterator` arrives from `IterableOnce` and `remove` from `mutable.MapOps`.
    Kind.Map.toString -> Set(
      ExternalSurface.Member("put", 2), ExternalSurface.Member("get", 1, ObjectArg),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("values", 0), ExternalSurface.Member("keys", 0),
    ),
    // `mutable.SetOps` declares `add`/`remove` CONCRETELY over `addOne`/`subtractOne`, which is
    // exactly the recursion this set exists to break; `contains` is `SetOps`', `iterator` is
    // `IterableOnce`'s.
    Kind.Set.toString -> Set(
      ExternalSurface.Member("contains", 1, ObjectArg), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
    ),
    // `SeqOps.size` is FINAL — the one row where the rename is not merely how the bridge names its
    // delegate but the only repair there is, which is what `ENGINE-LIMITS.md` K28's own table
    // already prescribed. `remove(int)` is `Buffer`'s abstract by-INDEX member (the by-value
    // overload java also declares is not one, hence the descriptor).
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

  // -------------------------------------------------------------------------------------------
  // THE JDK DEFAULTS A RE-PARENTING REMOVES — `ENGINE-LIMITS.md` K29
  // -------------------------------------------------------------------------------------------
  //
  // A class that DEFINES a collection inherits these from `java.util.AbstractCollection` and calls
  // them through `super`. Emitted `extends scala.collection.mutable.Set`, it has three of them not
  // at all and the fourth at a different formal and a different result — so the phase supplies the
  // body, standing on `this` where java stood on `super`.
  //
  // WHY THIS IS A TABLE AND NOT A RULE. The substitution is licensed by the JDK IMPLEMENTATION's own
  // dispatch, which is a fact about each member separately: these four reach nothing but PUBLIC
  // VIRTUAL members of the receiver, so a helper written over `this` computes what `super` named.
  // `ArrayList.clone` and `AbstractList.subList` are the counter-examples that make the point —
  // their bodies read the receiver's own FIELDS, which no helper standing on `this` can reach, and
  // `superPlaced`'s refusal is the right answer for them. Adding a fifth entry means reading that
  // member's JDK body and writing it down here; it is not a judgement about the member's NAME.

  /** member NAME → the [[balticporter.runtime.JavaCollections]] helper that reproduces its
    * `java.util.AbstractCollection` default. */
  private[balticporter] val VirtualJdkDefaults: Map[String, String] = Map(
    "containsAll" -> "containsAll",
    "addAll"      -> "addAll",
    "removeAll"   -> "removeAll",
    "retainAll"   -> "retainAll",
  )

  /** …and the BODY each entry above stands for, from the JDK's own source, so the licence is
    * READABLE at the emitted call rather than only in this file's comments. Rendered into the
    * decision, which is what puts it beside the code an agent in another repository is reading
    * (§4.575). Every member named in each of them is public and virtual on the receiver, which IS
    * the argument. */
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
         // …and the three `mutable.Buffer` members a re-parented `java.util.List` owes and java
         // declares no counterpart for at all (K28.1). Named `buffer*` rather than after the scala
         // member, because `insertAll` above is java's `addAll(int, Collection)` and two overloads
         // of one name at one receiver would be an ambiguity between a helper and the very member
         // that calls it.
         "bufferRemoveRange", "bufferInsertAll", "bufferPatchInPlace")

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
    // …and java's three RAW constants, which are FIELDS and not calls — see [[StaticFieldFactories]]
    // and `staticFieldRewrite`. They belong in THIS table and not in a second one: `jdk-surface` asks
    // one question of an external member, and a table split by node kind would report a member the
    // phase answers as the port's JDK wall.
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
    // `java.util.EnumSet` reaches its shim ENTIRELY through these six: the java type has no public
    // constructor, so a member missing here is a member with no way in at all.
    "java.util.EnumSet#noneOf",
    "java.util.EnumSet#allOf",
    "java.util.EnumSet#of",
    "java.util.EnumSet#copyOf",
    "java.util.EnumSet#range",
    "java.util.EnumSet#complementOf",
    // …and the primitive optionals' two, which are `Some`/`None` and need no runtime member.
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
                               "toArray", "subList",
                               // …and SE8's default methods on `List`/`Collection`, plus
                               // `AbstractCollection`'s two bulk MUTATORS, which scala's nearest
                               // members answer a different question for.
                               "sort", "removeIf", "containsAll", "removeAll", "retainAll",
                               "ensureCapacity",
                               // …and java's BIDIRECTIONAL cursor, which `JavaListIterator.over`
                               // writes through to this very buffer (`ENGINE-LIMITS.md` K23). It is
                               // on `Kind.Seq` alone because java declares `listIterator` on `List`
                               // and nowhere else.
                               "listIterator",
                               // …and its SIBLING, which K23 refused and wave 16 answered: java's
                               // `spliterator()` is a DEFAULT METHOD re-declared per owner with its
                               // own characteristics, so `orderedSpliterator` reproduces `List`'s
                               // (`ORDERED`, plus the `SIZED | SUBSIZED` java's own factory ORs in)
                               // rather than inheriting whatever an `asJava` wrapper reports.
                               "spliterator"),
    Kind.Map.toString   -> Set("get", "put", "remove", "containsKey", "entrySet", "values", "putIfAbsent",
                               "computeIfAbsent", "containsValue"),
    // …`contains` is here because the phase ANSWERS for it at a `Set`: at a widened `Object` probe
    // it rewrites to `setContains`, and at every other argument scala's own `Set.contains` IS java's
    // lookup, asking the PROBE's `equals` the way `HashMap.getNode` does. It is deliberately NOT on
    // `Kind.Seq`, where `Buffer.contains` asks the STORED element's — a hole `jdk-surface` should go
    // on reporting.
    Kind.Set.toString   -> Set("remove", "contains", "toArray", "removeIf", "containsAll",
                               "removeAll", "retainAll",
                               // …`Set.spliterator()`'s own default, which passes `DISTINCT` where
                               // `List`'s passes `ORDERED` — the reason the two kinds name two
                               // helpers rather than sharing one that takes a constant.
                               "spliterator"),
    Kind.Entry.toString -> Set("getKey", "getValue"),
    // a Stack's own five, PLUS everything `Kind.Seq` covers — the re-entry arm at the foot of
    // `rewrite` really does answer those for a stack receiver, so listing them here is the table
    // saying what the phase does rather than what one arm's pattern spells (and §"err WIDE" in
    // `CollectionsHandledDerivationSpec`: a name assigned to FEWER kinds than its arm covers makes
    // `jdk-surface` report a hole that is filled).
    // A Stack's own five — `empty` renamed, the other four DECLARED BY THE SHIM, which is a
    // rewrite the phase performed exactly as much as any other: `jdk-surface` asks "does this
    // phase answer for that member", and "the target already has it" is an answer.
    // the primitive optionals' renames. `orElseThrow` is the NO-ARGUMENT overload only; the
    // supplier one throws something else and has no arm, which is why it is absent here.
    Kind.Opt.toString   -> Set("getAsInt", "getAsLong", "getAsDouble", "orElseThrow",
                               "isPresent", "orElse", "ifPresent"),
    Kind.Stack.toString -> (Set("push", "pop", "peek", "search", "empty") ++
                            Set("get", "set", "remove", "addLast", "offer", "offerLast",
                                "addFirst", "offerFirst", "poll", "pollFirst", "peekFirst", "element",
                                "toArray", "subList")),
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
    * module (`balticporter/runtime/src/main/scala`), not here — see [[RuntimeArtifact]] for why a per-port copy
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
  val runtimeTypes: Set[String] =
    Set(JavaIteratorFqn, JavaListIteratorFqn, JavaIterableFqn, JavaCollectionFqn, JavaCollectionsFqn,
        JavaStackFqn, JavaEnumMapFqn, JavaEnumSetFqn,
        JavaOptionalIntFqn, JavaOptionalLongFqn, JavaOptionalDoubleFqn)

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
    * NOT the source of truth: this is the build-time copy of `balticporter/runtime/src/main/scala`, read back
    * off the classpath. PREFER `RuntimePlan.of(phases, mode).writeSources(dir)`, which will not
    * write them at all when the port depends on the artifact — which is the default and the
    * correct choice for any port that is one module of several. */
  lazy val runtimeSources: Map[String, String] =
    runtimeTypes.map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap
