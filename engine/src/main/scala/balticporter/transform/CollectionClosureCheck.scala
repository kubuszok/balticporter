package balticporter.transform

import balticporter.tir.{CheckReport, Origin, Program, SymId, Tree, UsageKind}

/** A retyping map must be CLOSED DOWNWARDS over the source library's own subtype relations — and
  * this counts every place `CollectionsTransform.typeMap` is not.
  *
  * ==The rule==
  * A mapping that sends `java.util.List` to `mutable.Buffer` has said something about every JDK
  * type that IS a `java.util.List`. If `java.util.Vector` is left unmapped, then after the phase
  * runs a `Vector`-typed expression and a `Buffer`-typed slot no longer meet — java's own
  * `Vector <: List` is gone, and nothing in the port records that it went. The same failure has
  * now been measured twice from the two opposite directions:
  *
  *   - `Collection` mapped to the shim while `AbstractCollection` was mapped to `Buffer`: **13 of
  *     simple-graphs' 20 errors** (ENGINE-LIMITS K5);
  *   - `ArrayDeque` mapped to `mutable.ArrayDeque` while `Queue` was mapped to `mutable.Queue`,
  *     which INVERTS the relation because scala orders those two types oppositely to java
  *     (`CollectionsTransform.typeMap`, the `java.util.Queue` entry).
  *
  * Both were found by a compile error in one corpus library, one library at a time. This turns the
  * property into a number on every run, for every JDK collection type, whether or not the corpus
  * happens to use it.
  *
  * ==What is universal and what is policy==
  * The CLOSURE PROPERTY is CLAUDE.md §1(a): "if a type maps, everything the JDK declares as its
  * subtype must map or be REPORTED" is a fact about java and scala and holds for every library.
  * WHICH types map is §1(b) — `CollectionsTransform.typeMap`'s own business — so this check takes
  * the mapped set as a PARAMETER and grows no mappings of its own. An empty mapped set makes it a
  * no-op by arithmetic (nothing is covered), which is the shape §1(b) asks for.
  *
  * ==Where the hierarchy comes from, and why it is DATA here==
  * From the JDK's own declarations, transcribed. The TIR cannot supply it: the frontend interns an
  * EXTERNAL symbol lazily with `owner = SymId.None`, no `Definition` and `info = NoType`
  * (CLAUDE.md §4.56), so a `java.util.Vector` symbol in the table carries its name and nothing
  * else — there is no parent list to climb. Reading the parents off a classpath would make the
  * check's answer depend on which JDK the migration ran under, for a hierarchy that has been
  * frozen since Java 1.2. The set is finite and closed, so it is written down, and
  * [[jdkSupertypes]] is checked by `CollectionClosureSpec` against the shapes the corpus uses.
  *
  * A type this table does not know is NOT reported — the honest failure direction, since the
  * alternative (guessing from the package) would report `java.util.Random` as an unmapped
  * collection. A missing edge is a missed finding; a wrong edge is a wrong finding.
  */
object CollectionClosureCheck:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-closure"

  /** CLAUDE.md §1's classification, stated once for the whole check: every finding here has the
    * same fix shape, which is what makes it a §1(b) — the mechanism (retype a JDK collection) is
    * already built and only the POLICY (which types) is short. */
  val Classification: String =
    "§1(b): add the type to CollectionsTransform.typeMap with a target that preserves the JDK " +
      "subtype relation, or record in the port's policy why the library never mixes the two."

  /** one referenced JDK collection type the mapping does not cover, at one usage site.
    *
    * `coveredBy` is the NEAREST mapped supertype, not merely some mapped ancestor: it is what a
    * reader needs to pick the target, since the faithful target for a subtype is a scala subtype
    * of whatever the supertype already maps to. */
  final case class Finding(tpe: String, coveredBy: String, mapsTo: String, origin: Origin, kind: UsageKind, enclosing: SymId):
    def detail: String = s"$kind — unmapped, but $coveredBy is mapped to $mapsTo, so the JDK relation $tpe <: $coveredBy is lost"
    def render: String = s"$tpe (covered by $coveredBy -> $mapsTo)  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, tpe, program.symbolOf(enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(origin.javaPath), origin.line, detail)

  /** DIRECT supertypes, as the JDK declares them. Interfaces and abstract bases alike, because a
    * port breaks on either — `AbstractCollection` is what a library EXTENDS and `Collection` is
    * what it DECLARES, and K5 is the two disagreeing.
    *
    * Nested types are keyed with `$`, which is the separator `Symbol.fullName` uses (CLAUDE.md
    * §4.56) and the spelling Spoon produces; the dotted alias is added below for a frontend that
    * names nested types with `.`, exactly as `typeMap` carries both.
    *
    * `java.util.RandomAccess` and `java.util.Dictionary` appear only as TARGETS. They are real JDK
    * supertypes and neither is mapped, so a walk that reaches one simply ends there — which is the
    * right answer: nothing about them relates to a mapped type. */
  val jdkSupertypes: Map[String, List[String]] = Map(
    // ---- the interface spine ----
    "java.util.Collection"             -> List("java.lang.Iterable"),
    "java.util.List"                   -> List("java.util.Collection"),
    "java.util.Set"                    -> List("java.util.Collection"),
    "java.util.SortedSet"              -> List("java.util.Set"),
    "java.util.NavigableSet"           -> List("java.util.SortedSet"),
    "java.util.Queue"                  -> List("java.util.Collection"),
    "java.util.Deque"                  -> List("java.util.Queue"),
    "java.util.SortedMap"              -> List("java.util.Map"),
    "java.util.NavigableMap"           -> List("java.util.SortedMap"),
    "java.util.ListIterator"           -> List("java.util.Iterator"),
    "java.util.PrimitiveIterator"      -> List("java.util.Iterator"),
    // ---- the abstract bases a library EXTENDS (K5) ----
    "java.util.AbstractCollection"     -> List("java.util.Collection"),
    "java.util.AbstractList"           -> List("java.util.AbstractCollection", "java.util.List"),
    "java.util.AbstractSequentialList" -> List("java.util.AbstractList"),
    "java.util.AbstractSet"            -> List("java.util.AbstractCollection", "java.util.Set"),
    "java.util.AbstractQueue"          -> List("java.util.AbstractCollection", "java.util.Queue"),
    "java.util.AbstractMap"            -> List("java.util.Map"),
    // ---- the concrete implementations ----
    "java.util.ArrayList"              -> List("java.util.AbstractList", "java.util.List", "java.util.RandomAccess"),
    "java.util.LinkedList"             -> List("java.util.AbstractSequentialList", "java.util.List", "java.util.Deque"),
    "java.util.Vector"                 -> List("java.util.AbstractList", "java.util.List", "java.util.RandomAccess"),
    "java.util.Stack"                  -> List("java.util.Vector"),
    "java.util.ArrayDeque"             -> List("java.util.AbstractCollection", "java.util.Deque"),
    "java.util.PriorityQueue"          -> List("java.util.AbstractQueue"),
    "java.util.HashSet"                -> List("java.util.AbstractSet", "java.util.Set"),
    "java.util.LinkedHashSet"          -> List("java.util.HashSet", "java.util.Set"),
    "java.util.TreeSet"                -> List("java.util.AbstractSet", "java.util.NavigableSet"),
    "java.util.EnumSet"                -> List("java.util.AbstractSet"),
    "java.util.HashMap"                -> List("java.util.AbstractMap", "java.util.Map"),
    "java.util.LinkedHashMap"          -> List("java.util.HashMap", "java.util.Map"),
    "java.util.TreeMap"                -> List("java.util.AbstractMap", "java.util.NavigableMap"),
    "java.util.IdentityHashMap"        -> List("java.util.AbstractMap", "java.util.Map"),
    "java.util.WeakHashMap"            -> List("java.util.AbstractMap", "java.util.Map"),
    "java.util.EnumMap"                -> List("java.util.AbstractMap"),
    "java.util.Hashtable"              -> List("java.util.Dictionary", "java.util.Map"),
    "java.util.Properties"             -> List("java.util.Hashtable"),
    // `Map.Entry`'s own implementations. `Map$Entry` itself has no supertype — it is a pair, which
    // is exactly why `typeMap` sends it to `Tuple2` rather than into the collection family.
    "java.util.AbstractMap$SimpleEntry"          -> List("java.util.Map$Entry"),
    "java.util.AbstractMap$SimpleImmutableEntry" -> List("java.util.Map$Entry"),
    // ---- java.util.concurrent: same spine, and a port that uses one of these is a port whose
    // collections cross the boundary in exactly the same way. (They are ALSO JVM-only, which
    // `PortabilityCheck` reports separately — two different facts about one reference.) ----
    "java.util.concurrent.ConcurrentMap"           -> List("java.util.Map"),
    "java.util.concurrent.ConcurrentNavigableMap"  -> List("java.util.concurrent.ConcurrentMap", "java.util.NavigableMap"),
    "java.util.concurrent.ConcurrentHashMap"       -> List("java.util.AbstractMap", "java.util.concurrent.ConcurrentMap"),
    "java.util.concurrent.ConcurrentSkipListMap"   -> List("java.util.AbstractMap", "java.util.concurrent.ConcurrentNavigableMap"),
    "java.util.concurrent.ConcurrentSkipListSet"   -> List("java.util.AbstractSet", "java.util.NavigableSet"),
    "java.util.concurrent.CopyOnWriteArrayList"    -> List("java.util.List", "java.util.RandomAccess"),
    "java.util.concurrent.CopyOnWriteArraySet"     -> List("java.util.AbstractSet"),
    "java.util.concurrent.BlockingQueue"           -> List("java.util.Queue"),
    "java.util.concurrent.BlockingDeque"           -> List("java.util.concurrent.BlockingQueue", "java.util.Deque"),
    "java.util.concurrent.TransferQueue"           -> List("java.util.concurrent.BlockingQueue"),
    "java.util.concurrent.ConcurrentLinkedQueue"   -> List("java.util.AbstractQueue"),
    "java.util.concurrent.ConcurrentLinkedDeque"   -> List("java.util.AbstractCollection", "java.util.Deque"),
    "java.util.concurrent.LinkedBlockingQueue"     -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingQueue"),
    "java.util.concurrent.LinkedBlockingDeque"     -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingDeque"),
    "java.util.concurrent.LinkedTransferQueue"     -> List("java.util.AbstractQueue", "java.util.concurrent.TransferQueue"),
    "java.util.concurrent.ArrayBlockingQueue"      -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingQueue"),
    "java.util.concurrent.PriorityBlockingQueue"   -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingQueue"),
    "java.util.concurrent.DelayQueue"              -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingQueue"),
    "java.util.concurrent.SynchronousQueue"        -> List("java.util.AbstractQueue", "java.util.concurrent.BlockingQueue"),
  ) ++ Map(
    // the dotted spelling of every `$`-keyed entry, so a frontend that names nested types with `.`
    // gets the same answer. Derived rather than written twice — a second copy is a second thing to
    // forget.
    "java.util.AbstractMap.SimpleEntry"          -> List("java.util.Map.Entry"),
    "java.util.AbstractMap.SimpleImmutableEntry" -> List("java.util.Map.Entry"),
  )

  /** every JDK type this table knows about at all — keys and targets alike. The targets matter:
    * `java.util.Dictionary` is named only as `Hashtable`'s parent and is still a type the family
    * contains. Used by [[CollectionBoundaryCheck]] to decide which side of a slot is JDK. */
  val jdkFamily: Set[String] = jdkSupertypes.keySet ++ jdkSupertypes.values.flatten

  /** transitive supertypes, NEAREST FIRST (breadth-first over the declared edges).
    *
    * Order is load-bearing, not cosmetic: the finding names the nearest mapped ancestor, and that
    * is what tells a reader which target preserves the relation — `LinkedHashSet`'s answer is
    * `HashSet`, not `Iterable`. */
  def supertypesOf(fqn: String): List[String] =
    val seen = collection.mutable.LinkedHashSet.empty[String]
    var frontier = jdkSupertypes.getOrElse(fqn, Nil)
    var fuel = 32
    while frontier.nonEmpty && fuel > 0 do
      val fresh = frontier.filterNot(seen.contains)
      fresh.foreach(seen += _)
      frontier = fresh.flatMap(jdkSupertypes.getOrElse(_, Nil))
      fuel -= 1
    seen.toList

  /** Every JDK collection type the program REFERENCES that `mapped` does not cover while covering
    * one of its JDK supertypes — one finding per usage site, as `PortabilityCheck` does, because
    * the location is what makes a finding actionable.
    *
    * `mapped` is `CollectionsTransform`'s `typeMap` keys. `mapsTo` is looked up through the same
    * map so the finding can say what the supertype became; a caller that only has the key set can
    * pass `_ => "?"`.
    *
    * NOT reported: a type `mapped` already contains (it maps, so nothing is lost), and a type no
    * mapped supertype covers (it is unrelated to anything this phase moved, so the phase has no
    * standing to say anything about it — CLAUDE.md §4.56). */
  def check(program: Program, mapped: Set[String], target: String => String = _ => "?"): List[Finding] =
    check(program, program.units, mapped, target)

  /** …restricted to the units the run actually EMITS.
    *
    * A DEPENDENT port resolves against another module's java (`FrontendConfig.resolutionRoots`),
    * so its `Program` carries units it will never write — and a finding attributed to one of those
    * is the BASE module's, reported by a repository that cannot act on it (ENGINE-LIMITS D2;
    * `OmissionCheck` and `PortabilityCheck.inEmittedCode` both carry the same filter for the same
    * reason). Measured here rather than assumed: unfiltered, this check reported the SAME two
    * findings for libGDX core, libGDX's test suite and both Ashley source sets — and only the
    * first pair is libGDX core's own. The other six were `AsyncExecutor`'s, seen four times.
    *
    * A BASE port passes `program.units` and this is the identity, so the no-op is the general path
    * taken to its limit rather than a branch around it. */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String], target: String => String): List[Finding] =
    val owned = ownedBy(program, units)
    program.symbols.all.toList.flatMap { sym =>
      if mapped.contains(sym.fullName) then Nil
      else
        supertypesOf(sym.fullName).find(mapped.contains) match
          case None    => Nil
          case Some(s) =>
            program.usages(sym.id).filter(u => owned(u.enclosing)).map(u =>
              Finding(sym.fullName, s, target(s), u.site.origin, u.kind, u.enclosing))
    }

  /** every symbol whose top-level owner is one of `units`. Fuel-bounded, and a symbol that cannot
    * be rooted counts as NOT owned — attributing another module's finding is the defect this
    * exists to prevent, so the failure direction is to say nothing. */
  private def ownedBy(program: Program, units: List[Tree.ClassDef]): SymId => Boolean =
    val roots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 &&
        (roots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))
    id => rooted(id, 64)

  /** grouped one-line summary, most-referenced first — the shape `PortabilityCheck.summary` has,
    * for the same reason: a per-site dump is what `findings.tsv` is for. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else fs.groupBy(f => (f.tpe, f.coveredBy, f.mapsTo)).toList.sortBy((k, v) => (-v.size, k._1))
      .map { case ((tpe, by, to), vs) => s"  $tpe: ${vs.size} site(s) — unmapped; $by is mapped to $to" }
      .mkString("\n")
