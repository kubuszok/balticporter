package balticporter.tir

/** MEMBER-LEVEL correspondence across a hierarchy — *the set of declarations that must change
  * together, or none of them* (DESIGN.md §8.5, §8.11).
  *
  * ==Why this exists==
  * The engine has parent edges between TYPES, recomputed locally at least six times in one file,
  * and has never had an edge between MEMBER symbols. Every seam that needed one matched on
  * `(name, arity)` instead, which `ENGINE-LIMITS.md` D1 measured insufficient — 263 findings, 118
  * ambiguous. So the edges here are keyed by NAME AND DESCRIPTOR ([[Symbol.descriptor]], §8.1's
  * identity), and where a descriptor is missing the edge is still taken but REPORTED
  * ([[OverrideGraph.Closure.approximate]]) rather than passed off as exact.
  *
  * ==The invariant, and why it is the whole point==
  * A signature change applies to '''all of a component or none of it'''. Renaming `Music#setLooping`
  * without `NoopMusic#setLooping` and every `new Music(){ … }` body is a silent contract break that
  * no count moves for; threading a `using` clause through half a component is a broken `override`.
  * [[closureOf]] answers what the component IS, and `Closure.isAnchored` answers whether the
  * program is allowed to change it at all.
  *
  * ==Three consumers, two of which use only this layer==
  *   - the property transform renames a component (through [[MemberRenamer]]);
  *   - the type-redirect member renames do the same;
  *   - globals→context (DESIGN.md §8.4) adds a `using` clause to every declaration in a component
  *     and never renames anything, which is exactly why the closure layer and the renamer are
  *     separate types.
  *
  * ==Anchors are deliberately CONSERVATIVE, and stated as such==
  * A parent type this program did not parse could declare the member under any name; with no
  * surface data for it the closure is ANCHORED and the consumer refuses, counted. *An over-refusal
  * is a counted skip an agent can see; an under-refusal is a silent contract break.* The surface
  * arrives as a value ([[ExternalSurface]]) rather than being guessed here, so the day the engine
  * derives a real JDK surface (DESIGN.md §8.9) the refusals lift with no change to this file.
  *
  * ==Built with `StandardTraversal`, so anonymous-class bodies are NODES==
  * CLAUDE.md §3: two of the four silent correctness defects this project has found were hand-rolled
  * walks that stopped one node short, and anonymous bodies are precisely where an override lives
  * that a class-only walk cannot see (156 sites in libGDX core). Enum-constant bodies are nodes for
  * the same reason — a Java enum constant may override the enum's own method.
  */
final class OverrideGraph private (
    private val program: Program,
    private val nodes: Map[SymId, OverrideGraph.Node],
    private val childrenBySym: Map[SymId, List[SymId]],
    private val external: ExternalSurface,
    private val baseUnits: Set[SymId],
):
  import OverrideGraph.*

  /** every TYPE this graph has a declaration for — owned classes, anonymous-class bodies and
    * enum-constant bodies alike. */
  def types: Set[SymId] = nodes.keySet

  /** `t`'s parent type symbols, in declaration order (superclass first, as the tree carries them).
    * Includes parents this program does NOT declare — [[externalParentsOf]] is the subset that
    * matters to an anchor decision. */
  def parentsOf(t: SymId): List[SymId] = nodes.get(t).map(_.parents).getOrElse(Nil)

  /** the types that name `t` as a parent. */
  def childrenOf(t: SymId): List[SymId] = childrenBySym.getOrElse(t, Nil)

  /** `t`'s parents that this program has no declaration for — a JDK type, a classpath jar. Named
    * by FQN, because that is the only handle there is for a type with no `ClassDef`. */
  def externalParentsOf(t: SymId): List[String] =
    nodes.get(t)
      .map(_.parents.filterNot(nodes.contains).flatMap(p => program.symbolOf(p).map(_.fullName)).filter(_.nonEmpty))
      .getOrElse(Nil)

  /** the member declarations `t` itself declares — methods and fields both, since a rename must be
    * held against a FIELD of the same name just as much as against a method. */
  def membersOf(t: SymId): List[SymId] = nodes.get(t).map(_.members).getOrElse(Nil)

  /** every strict ancestor of `t` this program DECLARES, transitively. Cycle-safe and memoised —
    * this is the walk the emitter re-derives six times over, published once. */
  def ancestorsOf(t: SymId): List[SymId] = ancestry(t)._1

  /** every strict ancestor of `t` this program did NOT parse, by FQN — the anchor candidates. */
  def externalAncestorsOf(t: SymId): List[String] = ancestry(t)._2

  /** every strict descendant of `t`, transitively — subclasses, anonymous bodies, enum constants. */
  def descendantsOf(t: SymId): List[SymId] = descendantsCache.getOrElseUpdate(t, {
    val out  = collection.mutable.LinkedHashSet.empty[SymId]
    val seen = collection.mutable.Set(t)
    val work = collection.mutable.Queue(childrenOf(t)*)
    while work.nonEmpty do
      val c = work.dequeue()
      if seen.add(c) then { out += c; childrenOf(c).foreach(work.enqueue) }
    out.toList
  })

  /** the types a change to a member of `t` is VISIBLE in: `t` itself, everything above it and
    * everything below it. What a collision test has to range over, and what a threading pass has to
    * reach. */
  def relativesOf(t: SymId): List[SymId] = (t :: ancestorsOf(t) ++ descendantsOf(t)).distinct

  /** the type that DECLARES `m`, as this graph indexes it. `SymId.None` for a symbol that is not a
    * member of any declared type (a local, a parameter, an external). */
  def ownerOf(m: SymId): SymId = memberOwner.getOrElse(m, SymId.None)

  /** `m`'s identity as an edge is keyed on — its name plus its parameter spelling. `None` for
    * anything that is not an executable: a Java FIELD does not override, it SHADOWS, and the two
    * are different facts (`TirEmitter.resolveFieldShadowing` is the one that handles the other). */
  def signatureOf(m: SymId): Option[Signature] = sigCache.get(m).flatten

  /** the parent-type declarations `m` overrides or implements, transitively — interfaces included,
    * and every one of them, since a diamond really does have two. */
  def overridden(m: SymId): List[SymId] =
    val owner = ownerOf(m)
    if owner == SymId.None then Nil
    else signatureOf(m).toList.flatMap(sig => ancestorsOf(owner).flatMap(a => matching(a, sig))).distinct

  /** the declarations BELOW `m` that override or implement it — every subclass, every anonymous
    * body, transitively. */
  def overriders(m: SymId): List[SymId] =
    val owner = ownerOf(m)
    if owner == SymId.None then Nil
    else signatureOf(m).toList.flatMap(sig => descendantsOf(owner).flatMap(d => matching(d, sig))).distinct

  /** THE CLOSURE — every declaration that must change together with `m`, plus the reasons this
    * program may not be allowed to change them.
    *
    * The walk is the connected component, not one hop up and one hop down: from every member it
    * reaches it walks up and down again, so two interfaces declaring the same signature and one
    * class implementing both close into ONE closure rather than two overlapping halves. Cycle-safe
    * (a corrupt parent edge cannot hang a phase) and order-independent (the result is a `Set`).
    *
    * A member that is not an executable is its own closure with no external anchors: a field does
    * not override anything, so nothing has to move with it.
    */
  def closureOf(m: SymId): Closure =
    signatureOf(m) match
      case scala.None => Closure(Set(m), Set.empty, baseAnchorsIn(Set(m)), Set.empty)
      case Some(_) =>
        val seen        = collection.mutable.LinkedHashSet.empty[SymId]
        val anchors     = collection.mutable.LinkedHashSet.empty[(String, String)]
        val approximate = collection.mutable.LinkedHashSet.empty[SymId]
        val work        = collection.mutable.Queue(m)
        while work.nonEmpty do
          val x = work.dequeue()
          if seen.add(x) then
            signatureOf(x).foreach { sig =>
              if sig.approximate then approximate += x
              val owner = ownerOf(x)
              // UP — every ancestor, owned or not. An owned ancestor contributes its matching
              // declaration; one this program never parsed contributes an ANCHOR unless the
              // supplied surface can prove the member is not there.
              ancestorsOf(owner).foreach(a => matching(a, sig).foreach(work.enqueue))
              externalAncestorsOf(owner).foreach { fqn =>
                if external.mayDeclare(fqn, sig) then anchors += (fqn -> sig.name)
              }
              // …and the one ancestor no Java tree ever shows: `java.lang.Object` is above every
              // type whether or not `SpoonTir.superTypes` lists it (it filters it out on purpose),
              // so a rename of `toString`/`equals`/`clone` would otherwise read as unanchored.
              if ExternalSurface.javaLangObjectDeclares(sig) then
                anchors += (ExternalSurface.JavaLangObject -> sig.name)
              // DOWN — every override below, anonymous and enum-constant bodies included.
              descendantsOf(owner).foreach(d => matching(d, sig).foreach(work.enqueue))
            }
        Closure(seen.toSet, anchors.toSet, baseAnchorsIn(seen.toSet), approximate.toSet)

  // -------------------------------------------------------------------------
  // internals
  // -------------------------------------------------------------------------

  private val memberOwner: Map[SymId, SymId] =
    nodes.values.iterator.flatMap(n => n.members.iterator.map(_ -> n.sym)).toMap

  /** one derivation per symbol, memoised: `closureOf` asks for the same signature repeatedly and
    * `Descriptor.ofInfo` walks a type every time it is asked. */
  private val sigCache: Map[SymId, Option[Signature]] =
    memberOwner.keysIterator.map(m => m -> deriveSignature(m)).toMap

  private def deriveSignature(m: SymId): Option[Signature] =
    program.symbolOf(m).filter(s => PolicyBinder.isExecutable(s.info)).map { s =>
      s.descriptor match
        case Some(d) => Signature(s.name, Some(d), arityOf(s.info), approximate = false)
        // D2's identity is the source; this is the residue it names — an EXTERNAL member the
        // frontend could not resolve, or one the ENGINE minted after the frontend ran. The edge is
        // still taken (refusing it would silently drop half a component) and it is REPORTED.
        case scala.None =>
          Signature(s.name, Descriptor.ofInfo(program, s.info), arityOf(s.info), approximate = true)
    }

  private def arityOf(t: TypeRepr): Int = t match
    case TypeRepr.MethodType(ps, _, _) => ps.size
    case TypeRepr.PolyType(_, r)       => arityOf(r)
    case _                             => 0

  /** the members of `t` that `sig` names. */
  private def matching(t: SymId, sig: Signature): List[SymId] =
    membersOf(t).filter(x => signatureOf(x).exists(_.matches(sig)))

  private val ancestryCache = collection.mutable.Map.empty[SymId, (List[SymId], List[String])]

  /** every strict ancestor of `t`: the owned ones by symbol, the unparsed ones by FQN. Cycle-safe
    * — a corrupt parent edge must not hang a phase. */
  private def ancestry(t: SymId): (List[SymId], List[String]) = ancestryCache.getOrElseUpdate(t, {
    val owned    = collection.mutable.LinkedHashSet.empty[SymId]
    val unparsed = collection.mutable.LinkedHashSet.empty[String]
    val seen     = collection.mutable.Set(t)
    val work     = collection.mutable.Queue(parentsOf(t)*)
    while work.nonEmpty do
      val p = work.dequeue()
      if seen.add(p) then
        if nodes.contains(p) then { owned += p; parentsOf(p).foreach(work.enqueue) }
        else program.symbolOf(p).map(_.fullName).filter(_.nonEmpty).foreach(unparsed += _)
    (owned.toList, unparsed.toList)
  })

  private val descendantsCache = collection.mutable.Map.empty[SymId, List[SymId]]

  /** the closure members this module does not OWN — a dependent's `Program` contains its base
    * (`ENGINE-LIMITS.md` D2), and renaming a base's declaration from a dependent emits a second,
    * disagreeing definition of it. */
  private def baseAnchorsIn(ms: Set[SymId]): Set[SymId] =
    if baseUnits.isEmpty then Set.empty else ms.filter(m => baseUnits.contains(unitOf(m)))

  /** the top-level unit `s` belongs to, by the owner climb `Program.owned` uses. Fuel-bounded. */
  private def unitOf(s: SymId, fuel: Int = 64): SymId =
    if s == SymId.None || fuel <= 0 then SymId.None
    else if unitSyms.contains(s) then s
    else program.symbolOf(s).map(x => unitOf(x.owner, fuel - 1)).getOrElse(SymId.None)

  private val unitSyms: Set[SymId] = program.units.map(_.symbol).toSet

object OverrideGraph:

  /** one declared TYPE: classes, anonymous bodies and enum-constant bodies alike. */
  private[tir] final case class Node(sym: SymId, parents: List[SymId], members: List[SymId])

  /** A member's identity as an EDGE is keyed on: its name and its parameter spelling.
    *
    * `arity` is carried beside the descriptor and used only when one side has none — the D1
    * fallback, kept so that a component is never silently split in half, and marked
    * [[Signature.approximate]] so nothing downstream can mistake it for the exact answer.
    */
  final case class Signature(name: String, descriptor: Option[Descriptor], arity: Int, approximate: Boolean):
    /** the same member, across two types. Descriptors decide when both sides have one; otherwise
      * arity does, and the side that lacked one is already flagged approximate. */
    def matches(that: Signature): Boolean =
      name == that.name && ((descriptor, that.descriptor) match
        case (Some(a), Some(b)) => a == b
        case _                  => arity == that.arity)

  /** WHAT MUST CHANGE TOGETHER, and what forbids changing it.
    *
    * @param members
    *   every declaration this program has for the component, `m` included — interface declarations,
    *   superclass declarations, every override below, every anonymous- and enum-constant-body
    *   implementation.
    * @param externalAnchors
    *   `(type FQN, member name)` for each parent type this program never parsed whose surface could
    *   not rule the member out. Non-empty ⇒ the component reaches a declaration this program does
    *   not own and cannot move.
    * @param baseAnchors
    *   declarations owned by a RESOLUTION ROOT rather than by this module (`ENGINE-LIMITS.md` D2).
    *   A subset of [[members]]: they are part of the component, and they are also the reason the
    *   component is frozen.
    * @param approximate
    *   members whose edge was taken by NAME AND ARITY because a descriptor was missing — reported,
    *   never guessed (DESIGN.md §8.11).
    */
  final case class Closure(
      members: Set[SymId],
      externalAnchors: Set[(String, String)],
      baseAnchors: Set[SymId],
      approximate: Set[SymId] = Set.empty,
  ):
    /** may this program change the component's signature at all? */
    def isAnchored: Boolean = externalAnchors.nonEmpty || baseAnchors.nonEmpty

    /** one sentence naming what froze it — the string a refusal reports, so an agent does not have
      * to re-derive it from two sets. `None` when nothing did. */
    def anchorReason(program: Program): Option[String] =
      val ext  = externalAnchors.toList.map((t, m) => s"$t#$m").sorted
      val base = baseAnchors.toList.flatMap(program.symbolOf).map(_.fullName).sorted
      (ext, base) match
        case (Nil, Nil) => scala.None
        case (e, Nil)   => Some(s"the component reaches ${e.size} declaration(s) this program does not " +
          s"parse and cannot move: ${e.mkString(", ")}")
        case (Nil, b)   => Some(s"the component reaches ${b.size} declaration(s) owned by a resolution " +
          s"root rather than by this module: ${b.mkString(", ")}")
        case (e, b)     => Some(s"the component reaches ${e.size} unparsed declaration(s) " +
          s"(${e.mkString(", ")}) and ${b.size} owned by a resolution root (${b.mkString(", ")})")

  /** Build the graph for one program.
    *
    * @param external
    *   what is known about the members of types this program did NOT parse. The default knows
    *   `java.lang.Object` and nothing else, which is the honest state of the engine today: every
    *   other unparsed parent anchors.
    * @param baseUnits
    *   the top-level units this run RESOLVES AGAINST but does not convert (`PortRun`'s foreign
    *   partition). Empty — the default, and the single-module case — means every unit is this
    *   module's and no closure is base-anchored.
    */
  def build(p: Program,
            external: ExternalSurface = ExternalSurface.default,
            baseUnits: Set[SymId] = Set.empty): OverrideGraph =
    given Program = p
    val collector = new Collector
    p.units.foreach(u => StandardTraversal.mapClassDef(collector, u))
    val nodes    = collector.nodes.map(n => n.sym -> n).toMap
    val children = nodes.values.toList
      .flatMap(n => n.parents.map(_ -> n.sym))
      .groupMap(_._1)(_._2)
      .view.mapValues(_.distinct).toMap
    new OverrideGraph(p, nodes, children, external, baseUnits)

  /** The walk. A `Phase` driven by [[StandardTraversal]] rather than a private recursion, so an
    * anonymous-class body is a node by construction and a node kind added tomorrow is reached
    * without an edit here (CLAUDE.md §3). It rewrites nothing — the rebuilt tree is thrown away,
    * which costs one allocation per node and buys the coverage guarantee. */
  private final class Collector extends Phase:
    def name: String = "override-graph/build"
    val nodes = collection.mutable.ListBuffer.empty[Node]

    override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
      nodes += Node(t.symbol, t.parents.flatMap(headSym), membersIn(t.body))
      // a Java enum CONSTANT may override the enum's own methods in its body, and that body is not
      // a `ClassDef` — so it is a node here or its overrides are invisible.
      t.enumCases.foreach(ec => nodes += Node(ec.symbol, List(t.symbol), membersIn(ec.body)))
      t

    override def transformNew(t: Tree.New)(using Program): Term =
      t.anon.foreach(a => nodes += Node(a.symbol, headSym(t.tpt).toList, membersIn(a.body)))
      t

    private def membersIn(body: List[Statement]): List[SymId] = body.collect {
      case d: Tree.DefDef => d.symbol
      case v: Tree.ValDef => v.symbol
    }

  private def headSym(t: Term | TypeTree): Option[SymId] = t match
    case tt: TypeTree => headOf(tt.tpe)
    case x: Term      => headOf(x.tpe)

  private def headOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headOf(tc)
    case _                           => scala.None

/** WHAT IS KNOWN about the members of types this program did not parse.
  *
  * A VALUE and not a predicate, for [[RuleScope]]'s reason: the answer has to be reportable. A
  * closure refused because `java.util.Map$Entry` might declare the member is a refusal an agent can
  * act on; a refusal from an opaque `(String, String) => Boolean` is not.
  *
  * ==The default knows what the PLATFORM fixes, and nothing else==
  * `java.lang.Object` is universal knowledge about Java — §1(a), not any library's policy — and it
  * has to be here because [[OverrideGraph]] cannot see it in the tree: the frontend filters
  * `java.lang.Object` out of every parent list on purpose, so without this a rename of `toString`
  * would read as unanchored. Beside it are the platform interfaces whose member sets are CLOSED
  * ([[ExternalSurface.jdkPlatform]]) — `Serializable` declares nothing, `Comparable` declares
  * `compareTo`, and no library can add to either. Everything else unparsed is [[mayDeclare]] =
  * true: refuse, count, and lift the refusal the day the type's surface is genuinely known.
  *
  * @param known
  *   FQN → the members that type declares. A type PRESENT here is answered exactly, so an absence
  *   from its set is proof. A type ABSENT here is unknown, and unknown anchors.
  */
final case class ExternalSurface(known: Map[String, Set[ExternalSurface.Member]] = Map.empty):

  /** could `fqn` declare something `sig` would rename? Unknown ⇒ YES, deliberately. */
  def mayDeclare(fqn: String, sig: OverrideGraph.Signature): Boolean =
    known.get(fqn) match
      case scala.None     => true
      case Some(declared) => declared.exists(_.matches(sig))

  /** is this type's member set known at all? — what a diagnostic prints beside a refusal. */
  def isKnown(fqn: String): Boolean = known.contains(fqn)

  def ++(that: ExternalSurface): ExternalSurface =
    ExternalSurface(that.known.foldLeft(known)((m, kv) => m.updated(kv._1, m.getOrElse(kv._1, Set.empty) ++ kv._2)))

object ExternalSurface:

  /** one member of an unparsed type. `descriptor` absent means "this arity, whatever the parameter
    * types" — which is what the engine's own arity-keyed channels carry, and which over-matches on
    * purpose. */
  final case class Member(name: String, arity: Int, descriptor: Option[Descriptor] = scala.None):
    def matches(sig: OverrideGraph.Signature): Boolean =
      name == sig.name && ((descriptor, sig.descriptor) match
        case (Some(a), Some(b)) => a == b
        case _                  => arity == sig.arity)

  val JavaLangObject = "java.lang.Object"

  /** `java.lang.Object`'s member set, which is fixed by the language. Above EVERY Java type whether
    * or not the tree says so. */
  val javaLangObjectMembers: Set[Member] = Set(
    Member("equals", 1, Some(Descriptor(List(Param.Named("Object"))))),
    Member("hashCode", 0, Some(Descriptor.empty)),
    Member("toString", 0, Some(Descriptor.empty)),
    Member("clone", 0, Some(Descriptor.empty)),
    Member("finalize", 0, Some(Descriptor.empty)),
    Member("getClass", 0, Some(Descriptor.empty)),
    Member("notify", 0, Some(Descriptor.empty)),
    Member("notifyAll", 0, Some(Descriptor.empty)),
    Member("wait", 0, Some(Descriptor.empty)),
    Member("wait", 1, Some(Descriptor(List(Param.Prim("long"))))),
    Member("wait", 2, Some(Descriptor(List(Param.Prim("long"), Param.Prim("int"))))),
  )

  /** does the implicit root declare this? Asked by every closure, whatever the tree shows. Matched
    * on the NAME alone, for `equals`'s sake: the frontend retypes a 1-argument `equals`'s parameter
    * to `scala.Any` before building the `MethodType` ([[Descriptor.ofInfo]]'s own note), so a
    * descriptor comparison there would answer no and un-anchor the one member most likely to be
    * overridden. The direction of the error is refusal, which is the safe one. */
  def javaLangObjectDeclares(sig: OverrideGraph.Signature): Boolean =
    javaLangObjectMembers.exists(_.name == sig.name)

  /** The PLATFORM interfaces whose member sets are fixed by the JDK, so an absence really is proof.
    *
    * ==Why these are §1(a) and a demand-derived surface is not==
    * `java.lang.Object` is here because the frontend hides it; these are here because they are
    * CLOSED. `java.io.Serializable` declares nothing at all, `java.lang.Comparable` declares
    * `compareTo`, `java.lang.Iterable` declares three methods, and no library can add to any of
    * them. That is a fact about Java in exactly the sense §1(a) means, and it is the only kind of
    * entry this map may hold: `known`'s contract is that a type present here is answered EXACTLY,
    * so an absence from its member set is proof and the anchor lifts.
    *
    * A surface derived from what a program CALLS cannot go here, however tempting the seam
    * (`ENGINE-LIMITS.md` K12 said it could, and that is now corrected there with the measurement).
    * The rows say which members a program references, not which members a type declares, so an
    * absence from them proves nothing — and turning `OverrideGraph`'s counted over-refusal into an
    * unnoticed under-refusal is the trade DESIGN.md §8.5 explicitly chose against.
    *
    * ==Arity-only on purpose==
    * No `descriptor`, so [[Member.matches]] falls back to name-and-arity, which OVER-matches. The
    * direction of that error is refusal, which is the safe one — the same reason
    * [[javaLangObjectDeclares]] matches on the name alone.
    *
    * Nothing here is a library's policy and nothing here is a guess: each set is the whole of what
    * the interface declares in the Java it is written for. A type whose surface is large or version-
    * dependent (`java.util.Comparator`, whose default methods grew across releases) is deliberately
    * ABSENT — unknown anchors, and an incomplete entry is worse than no entry.
    *
    * ==VERSION-DEPENDENT IS NOT UNENUMERABLE, and `java.lang.CharSequence` is the difference==
    * The paragraph above collapses two reasons into one, and `CharSequence` is the type that pulls
    * them apart. It was left out because its surface GREW (`chars()` in 8, `isEmpty()` in 15) — a
    * true observation and not, on its own, an argument: the frontend PINS a compliance level
    * (`SpoonTir.buildModel` sets 21), so "which members does this interface declare" has exactly one
    * answer for every tree this engine ever sees, and it is enumerable. `Comparator` stays out for
    * the OTHER reason — its surface is large enough that writing it down is a transcription somebody
    * would have to keep true — and the two are different failures. State it, and the anchor for a
    * java FIELD named `chars` on a `CharSequence` implementor lifts; leave it out, and the field
    * cannot be renamed, which `RefChecks` reports as `private variable chars cannot override method
    * chars` on the day the port reaches zero (`ENGINE-LIMITS.md` K28.2).
    *
    * The set is the INSTANCE members only. `CharSequence.compare(CharSequence, CharSequence)` (11)
    * is `static`, and a java interface's statics are not inherited at all (JLS 8.4.8), so it is not
    * a member any implementor could be answering for — including it would anchor a field named
    * `compare` on evidence that does not exist.
    */
  val jdkPlatform: Map[String, Set[Member]] = Map(
    "java.io.Serializable"     -> Set.empty,
    "java.lang.Cloneable"      -> Set.empty,
    "java.lang.Comparable"     -> Set(Member("compareTo", 1)),
    "java.lang.Iterable"       -> Set(Member("iterator", 0), Member("forEach", 1), Member("spliterator", 0)),
    "java.lang.Runnable"       -> Set(Member("run", 0)),
    "java.lang.AutoCloseable"  -> Set(Member("close", 0)),
    "java.io.Closeable"        -> Set(Member("close", 0)),
    "java.lang.CharSequence"   -> Set(Member("length", 0), Member("charAt", 1), Member("isEmpty", 0),
                                      Member("subSequence", 2), Member("toString", 0),
                                      Member("chars", 0), Member("codePoints", 0)),
    "java.util.Iterator"       -> Set(Member("hasNext", 0), Member("next", 0), Member("remove", 0),
                                      Member("forEachRemaining", 1)),
  )

  /** the default: `java.lang.Object`, plus the platform interfaces whose surfaces are closed. */
  val default: ExternalSurface = ExternalSurface(Map(JavaLangObject -> javaLangObjectMembers) ++ jdkPlatform)

  /** …from the arity-keyed channel the emitter already threads (`RuntimePlan.concreteMembers`,
    * `TirEmitter.externalConcrete`): FQN → `(name, params per clause)`. Arity-only, so it
    * over-matches — which for an anchor decision is the conservative direction. */
  def fromArities(m: Map[String, Set[(String, List[Int])]]): ExternalSurface =
    default ++ ExternalSurface(m.view.mapValues(_.map((n, ps) => Member(n, ps.sum))).toMap)
