package balticporter.tir

/** MEMBER-LEVEL correspondence across a hierarchy — *the set of declarations that must change
  * together, or none of them* (DESIGN.md §8.5, §8.11). Edges keyed by NAME AND DESCRIPTOR
  * ([[Symbol.descriptor]]) — `(name, arity)` alone was measured insufficient (`ENGINE-LIMITS.md`
  * D1). [[closureOf]] answers what a component IS; `Closure.isAnchored` whether it may be changed
  * at all, CONSERVATIVELY. Built with `StandardTraversal` so anon/enum bodies are NODES (§3). */
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
    else signatureOf(m).toList.flatMap(sig => ancestorsOf(owner).flatMap(a => matchingUp(a, sig, owner))).distinct

  /** the declarations BELOW `m` that override or implement it — every subclass, every anonymous
    * body, transitively. */
  def overriders(m: SymId): List[SymId] =
    val owner = ownerOf(m)
    if owner == SymId.None then Nil
    else signatureOf(m).toList.flatMap(sig => descendantsOf(owner).flatMap(d => matchingDown(d, sig, owner))).distinct

  /** THE CLOSURE — every declaration that must change together with `m`, plus the reasons this
    * program may not be allowed to change them. The walk is the connected COMPONENT (not one hop
    * up/down), cycle-safe and order-independent. A non-executable member (e.g. a field) is its own
    * closure with no external anchors. */
  def closureOf(m: SymId): Closure =
    signatureOf(m) match
      // …and a PRIVATE member takes the same arm for the same kind of reason: a field does not
      // override, and a private method is not INHERITED (JLS 8.2), so neither has anything above or
      // below it that has to move with it — nor any unparsed ancestor that could be declaring what
      // it overrides. See [[inherited]].
      case _ if !inherited(m) => Closure(Set(m), Set.empty, baseAnchorsIn(Set(m)), Set.empty)
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
              ancestorsOf(owner).foreach(a => matchingUp(a, sig, owner).foreach(work.enqueue))
              externalAncestorsOf(owner).foreach { fqn =>
                if external.mayDeclare(fqn, sig) then anchors += (fqn -> sig.name)
              }
              // …and the one ancestor no Java tree ever shows: `java.lang.Object` is above every
              // type whether or not `SpoonTir.superTypes` lists it (it filters it out on purpose),
              // so a rename of `toString`/`equals`/`clone` would otherwise read as unanchored.
              if ExternalSurface.javaLangObjectDeclares(sig) then
                anchors += (ExternalSurface.JavaLangObject -> sig.name)
              // DOWN — every override below, anonymous and enum-constant bodies included.
              descendantsOf(owner).foreach(d => matchingDown(d, sig, owner).foreach(work.enqueue))
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

  /** the members of ANCESTOR `a` that `sig` names, `sig` being declared in `from`.
    *
    * Read through the arguments `from` instantiates `a` with — see [[asSeenFrom]]. */
  private def matchingUp(a: SymId, sig: Signature, from: SymId): List[SymId] =
    membersOf(a).filter(inherited)
      .filter(x => signatureOf(x).exists(s => s.matches(sig) || asSeenFrom(s, a, from).matches(sig)))

  /** IS THIS MEMBER IN AN OVERRIDE RELATION AT ALL? — JLS 8.2/8.4.8.1. Only `private` answers `no`
    * outright (NOT INHERITED, 8.2): it is its own component, no ancestor can declare what it
    * overrides. `static` HIDES rather than overrides and still participates (a hiding pair must
    * move together). A wrong answer here over-approximates the closure, which is the honest
    * anchor — measured freezing five components on `java.lang.Enum#getBundle`. */
  private def inherited(m: SymId): Boolean =
    !program.symbolOf(m).exists(_.flags.isPrivate)

  /** the members of DESCENDANT `d` that `sig` names, `sig` being declared in ancestor `a`. The
    * mirror of [[matchingUp]]: it is the ANCESTOR's signature that carries the type parameters, so
    * this substitutes `sig` rather than the candidate. */
  private def matchingDown(d: SymId, sig: Signature, a: SymId): List[SymId] =
    val seen = asSeenFrom(sig, a, d)
    membersOf(d).filter(inherited)
      .filter(x => signatureOf(x).exists(s => s.matches(sig) || s.matches(seen)))

  /** `sig`, declared in `ancestor`, spelled as the descendant `from` sees it. A descriptor spells a
    * parameter by SIMPLE NAME, so java's one member (JLS 8.4.2) resolved through two type-parameter
    * spellings compares as two strings; this substitutes through the `extends` clause (EXACT,
    * `ParentSubst`'s own claim, resolved by symbol not name). Only ADDS edges — tried second, after
    * the unsubstituted comparison, since a lost edge SHRINKS a closure (DESIGN.md §8.5). */
  private def asSeenFrom(sig: Signature, ancestor: SymId, from: SymId): Signature =
    val byName = tparamSpelling(ancestor, from)
    if byName.isEmpty then sig
    else sig.copy(descriptor = sig.descriptor.map(d => Descriptor(d.params.map(substParam(_, byName)))))

  private def substParam(p: Param, m: Map[String, Param]): Param = p match
    case Param.Named(n) => m.getOrElse(n, p)
    case Param.Arr(of)  => substParam(of, m) match
      case Param.Unresolved => Param.Unresolved
      case inner            => Param.Arr(inner)
    case other => other

  private val spellingCache = collection.mutable.Map.empty[(SymId, SymId), Map[String, Param]]

  /** `ancestor`'s own type parameters, by written name, as `from` instantiates them. */
  private def tparamSpelling(ancestor: SymId, from: SymId): Map[String, Param] =
    spellingCache.getOrElseUpdate((ancestor, from), {
      val tps = program.definitionOf(ancestor).collect { case c: Tree.ClassDef => c.tparams.map(_.symbol) }
        .getOrElse(Nil)
      if tps.isEmpty then Map.empty
      else
        val subst = substOf(from)
        tps.flatMap(tp => (subst.get(tp), program.symbolOf(tp).map(_.name)) match
          case (Some(arg), Some(n)) if n.nonEmpty => Some(n -> Descriptor.paramOfType(program, arg))
          case _                                  => scala.None).toMap
    })

  private val substCache = collection.mutable.Map.empty[SymId, Map[SymId, TypeRepr]]

  private def substOf(t: SymId): Map[SymId, TypeRepr] =
    substCache.getOrElseUpdate(t,
      ParentSubst.ofParents(nodes.get(t).map(_.parentTypes).getOrElse(Nil))(using program))

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

  /** one declared TYPE: classes, anonymous bodies and enum-constant bodies alike. `parentTypes`
    * carries the same edges as `parents` WITH their type arguments, for reading an override edge
    * across a generic parent ([[OverrideGraph.asSeenFrom]]). Two lists since other consumers want
    * the head symbol without re-deriving it. */
  private[tir] final case class Node(sym: SymId, parents: List[SymId], members: List[SymId],
                                     parentTypes: List[TypeRepr] = Nil)

  /** A member's identity as an EDGE, keyed on name and parameter spelling. `arity` is the D1
    * fallback used only when one side has no descriptor, so a component is never silently split —
    * and [[Signature.approximate]] marks it so nothing downstream mistakes it for exact. */
  final case class Signature(name: String, descriptor: Option[Descriptor], arity: Int, approximate: Boolean):
    /** the same member, across two types. Descriptors decide when both sides have one; otherwise
      * arity does, and the side that lacked one is already flagged approximate. */
    def matches(that: Signature): Boolean =
      name == that.name && ((descriptor, that.descriptor) match
        case (Some(a), Some(b)) => a == b
        case _                  => arity == that.arity)

  /** WHAT MUST CHANGE TOGETHER, and what forbids changing it. @param members every declaration
    * this program has for the component, `m` included @param externalAnchors `(type FQN, member
    * name)` for a parent this program never parsed whose surface could not rule the member out —
    * non-empty means the component cannot move @param baseAnchors members owned by a RESOLUTION
    * ROOT (D2) @param approximate members whose edge was taken by NAME+ARITY, reported. */
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
    * @param external what is known about types this program did NOT parse — default knows only
    *   `java.lang.Object`, so every other unparsed parent anchors
    * @param baseUnits top-level units this run RESOLVES AGAINST but does not convert (`PortRun`'s
    *   foreign partition) — empty (default) means no closure is base-anchored. */
  def build(p: Program,
            external: ExternalSurface = ExternalSurface.default,
            baseUnits: Set[SymId] = Set.empty): OverrideGraph =
    given Program = p
    val collector = new Collector
    p.units.foreach(u => StandardTraversal.mapClassDef(collector, u))
    // Interned classpath types participate in ancestry resolution (K18).
    p.internedDefs.foreach(u => StandardTraversal.mapClassDef(collector, u))
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
      nodes += Node(t.symbol, t.parents.flatMap(headSym), membersIn(t.body), t.parents.map(typeOf))
      // a Java enum CONSTANT may override the enum's own methods in its body, and that body is not
      // a `ClassDef` — so it is a node here or its overrides are invisible.
      t.enumCases.foreach(ec =>
        nodes += Node(ec.symbol, List(t.symbol), membersIn(ec.body), List(TypeRepr.TypeRef(TypeRepr.NoPrefix, t.symbol))))
      t

    override def transformNew(t: Tree.New)(using Program): Term =
      t.anon.foreach(a => nodes += Node(a.symbol, headSym(t.tpt).toList, membersIn(a.body), List(t.tpt.tpe)))
      t

    private def membersIn(body: List[Statement]): List[SymId] = body.collect {
      case d: Tree.DefDef => d.symbol
      case v: Tree.ValDef => v.symbol
    }

  private def typeOf(t: Term | TypeTree): TypeRepr = t match
    case tt: TypeTree => tt.tpe
    case x: Term      => x.tpe

  private def headSym(t: Term | TypeTree): Option[SymId] = t match
    case tt: TypeTree => headOf(tt.tpe)
    case x: Term      => headOf(x.tpe)

  private def headOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headOf(tc)
    case _                           => scala.None

/** WHAT IS KNOWN about the members of types this program did not parse — a VALUE not a predicate,
  * so a refusal is reportable ([[RuleScope]]'s reason). Default knows `java.lang.Object`
  * (§1a — the frontend filters it from parent lists) plus the CLOSED platform interfaces
  * ([[ExternalSurface.jdkPlatform]]); everything else unparsed is [[mayDeclare]] = true: refuse,
  * count, lift the day the surface is known. @param known FQN → declared members; absent = unknown, anchors. */
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

  /** The PLATFORM interfaces whose member sets are fixed by the JDK, so an absence really is
    * proof — CLOSED (§1a), never a demand-derived surface (`ENGINE-LIMITS.md` K12). Arity-only
    * (over-matches toward refusal, the safe direction); a version-dependent or large surface
    * (`Comparator`) stays deliberately ABSENT — unknown anchors. `java.lang.Enum` is deliberately
    * absent too: stating it measured WORSE (`ENGINE-LIMITS.md` CT10, 32→41 errors). */
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
    // `java.lang.Enum` is NOT here, and the note above says why — it is a refusal with a number
    // (`ENGINE-LIMITS.md` CT10), not an omission.
  )

  /** the default: `java.lang.Object`, plus the platform interfaces whose surfaces are closed. */
  val default: ExternalSurface = ExternalSurface(Map(JavaLangObject -> javaLangObjectMembers) ++ jdkPlatform)

  /** …from the arity-keyed channel the emitter already threads (`RuntimePlan.concreteMembers`,
    * `TirEmitter.externalConcrete`): FQN → `(name, params per clause)`. Arity-only, so it
    * over-matches — which for an anchor decision is the conservative direction. */
  def fromArities(m: Map[String, Set[(String, List[Int])]]): ExternalSurface =
    default ++ ExternalSurface(m.view.mapValues(_.map((n, ps) => Member(n, ps.sum))).toMap)
