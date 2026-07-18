package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** Cross-unit constructor knowledge for the no-arg-primary funnel (the encoding the
  * hand-ported corpus uses for Node-family hierarchies):
  *
  *   class Emphasis extends DelimitedNodeImpl {          // no-arg primary
  *     def this(chars: BasedSequence) = { this(); <inlined super-overload effects> }
  *   }
  *
  * Applicable when every ctor's transitive super-chain bottoms out through ancestors
  * reachable via empty no-arg construction, and overload effects are replayable as
  * post-`this()` statements (field assignments are — translated fields are vars).
  */
final case class CtorInfo(
    superFqcn: Option[String],
    /** Java ctors: (params, superArgs (None = implicit super()), thisArgs, body). */
    ctors: List[(List[BParam], Option[List[BExpr]], Option[List[BExpr]], List[BStmt])],
):
  def noArgCtor: Option[(List[BParam], Option[List[BExpr]], Option[List[BExpr]], List[BStmt])] =
    ctors.find(_._1.isEmpty)

final class CtorRegistry(units: List[BUnit]):
  val byFqcn: Map[String, (BTypeDecl, CtorInfo)] =
    def entry(fqcn: String, t: BTypeDecl): List[(String, (BTypeDecl, CtorInfo))] =
      val info = CtorInfo(
        t.superClass.map(_.qname),
        t.ctors.map(c => (c.params, c.superArgs, c.thisArgs, c.body)),
      )
      // nested types register under Outer$Nested — Spoon qualifies super refs that way
      (fqcn -> (t, info)) :: (t.nested ++ t.inner).flatMap(n => entry(s"$fqcn$$${n.name}", n))
    units.flatMap { u =>
      u.types.flatMap { t =>
        entry(if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}", t)
      }
    }.toMap

  /** Resolves a type decl's registry key when the caller only knows package +
    * simple name (nested decls live under Outer$Name): exact key first, else
    * the UNIQUE in-package key ending in `$Name` — ambiguity returns None. */
  def resolveFqcn(pkg: String, name: String): Option[String] =
    val plain = if pkg.isEmpty then name else s"$pkg.$name"
    if byFqcn.contains(plain) then Some(plain)
    else
      val prefix = if pkg.isEmpty then "" else pkg + "."
      byFqcn.keysIterator
        .filter(k => k.startsWith(prefix) && k.endsWith("$" + name))
        .toList match
        case k :: Nil => Some(k)
        case _        => None

  /** the registry key of a superclass/interface reference (nested types live
    * under Outer$Name; interfaces arrive as dotted qnames Spoon resolved). */
  private def keyOf(qname: String): Option[String] =
    val dollar = qname.replace('.', '$')
    if byFqcn.contains(qname) then Some(qname)
    else byFqcn.keysIterator.find(k => k == qname || k.replace('.', '$').endsWith(dollar))

  /** true when any supertype (superclass chain + all interfaces, transitively,
    * within the closure) declares a nilary method named `name` — i.e. the accessor
    * fulfils an inherited contract and CANNOT be dropped (a `val` can't satisfy a
    * `def m(): T`, and interface-typed call sites dispatch through the method). */
  private def nilaryInSupertype(fqcn: String, name: String, depth: Int = 0): Boolean =
    if depth > 12 then return false
    byFqcn.get(fqcn) match
      case None => false
      case Some((t, info)) =>
        val supers = info.superFqcn.toList ++ t.interfaces.collect { case BType.Ref(q, _) => q }
        supers.flatMap(keyOf).exists { sk =>
          byFqcn.get(sk).exists { (st, _) =>
            (st.methods ++ st.staticMethods).exists(m => m.name == name && m.params.isEmpty)
          } || nilaryInSupertype(sk, name, depth + 1)
        }

  /** Java-style redundant accessors: a field `f` plus a nilary same-name method
    * whose body is exactly `return f`. Scala can't declare both — the method is
    * DROPPED and every resolved nilary call `x.f()` rewrites to the field read
    * (semantically exact: the method returned the field). Keys are ($-qualified
    * fqcn, member name). NOT collapsed when the method overrides an inherited
    * nilary member (the field can't satisfy the contract) or is `@Override`. */
  lazy val collapsedAccessors: Set[(String, String)] =
    byFqcn.iterator.flatMap { case (fqcn, (t, _)) =>
      def returnsField(body: Option[List[BStmt]], name: String, static: Boolean): Boolean =
        body.map(_.filterNot(st => st.k == BStmtK.Empty)).exists {
          case List(BStmt(_, BStmtK.Return(Some(Ident(n, k))))) =>
            n == name && (k match
              case RefKind.OwnField       => !static
              case RefKind.StaticField(_) => static
              case _                      => false)
          case _ => false
        }
      def collapsible(m: BMethod, hasField: Boolean, static: Boolean): Boolean =
        m.params.isEmpty && hasField && returnsField(m.body, m.name, static) &&
          !m.mods.isOverride && !nilaryInSupertype(fqcn, m.name)
      val inst = t.methods.collect {
        case m if collapsible(m, t.fields.exists(_.name == m.name), static = false) => (fqcn, m.name)
      }
      val stat = t.staticMethods.collect {
        case m if collapsible(m, t.staticFields.exists(_.name == m.name), static = true) => (fqcn, m.name)
      }
      inst ++ stat
    }.toSet

  /** Same-name field/method clashes where the method is @Deprecated and NOT a pure
    * accessor: the hand-port corpus's answer (ssg-md Parsing.ADDITIONAL_CHARS) is
    * keep the field, drop the deprecated method. Its trivia hoists to the field;
    * any surviving call sites surface at the scalac gate. */
  lazy val droppedDeprecatedClashes: Set[(String, String)] =
    def deprecated(m: BMethod): Boolean =
      m.mods.annotations.exists(a => a.qname == "scala.deprecated" || a.qname == "java.lang.Deprecated")
    byFqcn.iterator.flatMap { case (fqcn, (t, _)) =>
      val inst = t.methods.collect {
        case m if m.params.isEmpty && deprecated(m) && t.fields.exists(_.name == m.name) => (fqcn, m.name)
      }
      val stat = t.staticMethods.collect {
        case m if m.params.isEmpty && deprecated(m) && t.staticFields.exists(_.name == m.name) => (fqcn, m.name)
      }
      inst ++ stat
    }.toSet.diff(collapsedAccessors)

  /** fqcns whose no-arg construction path is empty-effect (transitively):
    * no ctors at all, or an explicit no-arg ctor with empty body and an
    * empty-no-arg-reachable parent.
    */
  lazy val noArgReachable: Set[String] =
    var acc = Set.empty[String]
    var changed = true
    def parentOk(info: CtorInfo): Boolean = info.superFqcn match
      case None    => true // extends Object
      case Some(p) => acc.contains(p) || !byFqcn.contains(p) // outside set: assume Java-visible no-arg (verified by scalac)
    while changed do
      changed = false
      byFqcn.foreach { case (fqcn, (_, info)) =>
        if !acc.contains(fqcn) then
          val ok = info.ctors.isEmpty && parentOk(info) ||
            info.noArgCtor.exists { case (_, superArgs, thisArgs, body) =>
              body.forall(_.k == BStmtK.Empty) && thisArgs.isEmpty &&
              superArgs.forall(_.isEmpty) && parentOk(info)
            }
          if ok then
            acc += fqcn
            changed = true
      }
    acc

  /** Inlines the transitive effects of calling `super(<args>)` on `parentFqcn`:
    * the matched overload's body with params substituted, prefixed by ITS super
    * chain's effects. None when not mechanically replayable.
    */
  private val debug = sys.env.contains("BP_DEBUG")
  private def miss(why: String): Option[List[BStmt]] =
    if debug then System.err.println(s"[ctor-inline miss] $why")
    None

  /** Deterministic serialization of everything the registry can inject into a
    * unit's output (ctor shapes + bodies, super chain, field mods for widening) —
    * cache keys include its digest so a parent ctor BODY edit invalidates
    * dependents (interface hashes alone only cover signature surface). */
  lazy val digestInput: String =
    byFqcn.toList.sortBy(_._1).map { case (fqcn, (t, info)) =>
      val fields = t.fields.map(f => s"${f.name}:${f.mods.vis}:${f.mods.isFinal}").mkString(",")
      s"$fqcn|${info.superFqcn.getOrElse("")}|$fields|${info.ctors.toString}"
    }.mkString("\n")

  /** Private non-final parent fields that some subclass's super-chain ctor effects
    * assign — the effect-replay funnel makes the subclass write them, so the parent
    * emits them `protected var` (the hand-ported corpus widens the same way:
    * ssg-md Node.chars). Structural overapproximation: computed from ctor shapes
    * alone, independent of which funnel strategy each subclass ends up using —
    * deterministic, and widening private→protected never changes behavior. */
  lazy val widenedFields: Set[(String, String)] =
    val out = collection.mutable.Set[(String, String)]()
    def walk(rootFqcn: String, fqcn: String, args: List[BExpr], depth: Int): Unit =
      if depth > 8 then return
      byFqcn.get(fqcn).foreach { case (pd, pi) =>
        pi.ctors.find(_._1.length == args.length).foreach { case (_, superArgs, thisArgs, body) =>
          if fqcn != rootFqcn then
            body.foreach { st =>
              st.k match
                case BStmtK.Assign(Ident(f, RefKind.OwnField), _, _)
                    if pd.fields.exists(fd => fd.name == f && fd.mods.vis == Vis.Private && !fd.mods.isFinal) =>
                  out += ((fqcn, f))
                case _ => ()
            }
          thisArgs match
            case Some(ta) => walk(rootFqcn, fqcn, ta, depth + 1)
            case None     => pi.superFqcn.foreach(p => walk(rootFqcn, p, superArgs.getOrElse(Nil), depth + 1))
        }
      }
    byFqcn.foreach { case (fqcn, (_, info)) =>
      info.ctors.foreach { case (_, superArgs, thisArgs, _) =>
        thisArgs match
          case Some(ta) => walk(fqcn, fqcn, ta, 0)
          case None     => info.superFqcn.foreach(p => walk(fqcn, p, superArgs.getOrElse(Nil), 0))
      }
    }
    out.toSet

  /** true when `body` (a parent ctor's effect statements) can legally execute
    * inside a subclass: assigned fields must resolve in the translated chain as
    * assignable there (var + visible, widening included), and read/called `this`
    * members must not be private at their declaring level (unless widened). */
  private def replayableFrom(declaringFqcn: String, body: List[BStmt]): Boolean =
    def findField(fqcn: String, name: String): Option[(String, Mods)] =
      byFqcn.get(fqcn) match
        case None => None
        case Some((decl, info)) =>
          decl.fields.find(_.name == name).map(f => (fqcn, f.mods))
            .orElse(info.superFqcn.flatMap(findField(_, name)))
    def privateHere(name: String): Boolean =
      byFqcn.get(declaringFqcn).exists { (decl, _) =>
        (decl.fields.exists(f => f.name == name && f.mods.vis == Vis.Private) &&
          !widenedFields((declaringFqcn, name))) ||
        decl.methods.exists(m => m.name == name && m.mods.vis == Vis.Private)
      }
    var ok = true
    def checkExpr(e: BExpr): BExpr =
      e match
        case Ident(n, RefKind.OwnField) if privateHere(n) => ok = false
        case Call(Recv.OnThis, m, _, _, _) if privateHere(m) => ok = false
        case _ => ()
      e
    def checkStmt(st: BStmt): Unit =
      st.k match
        case BStmtK.Assign(Ident(f, RefKind.OwnField), _, _) =>
          findField(declaringFqcn, f) match
            case Some((declFqcn, mods)) =>
              val visible = mods.vis != Vis.Private || widenedFields((declFqcn, f))
              if mods.isFinal || !visible then ok = false
            case None => ok = false // declared outside the translated set — unknowable
        case _ => ()
      BirTransform.mapStmt(st)(checkExpr)
    body.foreach(checkStmt)
    ok

  /** Follows `parentFqcn`'s own `this(...)`-delegation chain from a call of the
    * given arity to the terminal (non-this-delegating) constructor, returning the
    * args that reach it — with the caller's arg expressions substituted for the
    * intermediate params. This is how a subclass ctor `super(subsetArgs)` finds the
    * canonical full-arity super args to delegate through (OrderedMap(capacity) →
    * this(capacity, null) → the (int, host) canonical → [capacity, null]).
    * None when no matching-arity ctor exists in the closure. */
  def resolveThisChain(parentFqcn: String, args: List[BExpr], depth: Int = 0): Option[List[BExpr]] =
    if depth > 12 then return None
    byFqcn.get(parentFqcn).flatMap { (_, info) =>
      info.ctors.find(_._1.length == args.length) match
        case None => if depth == 0 then None else Some(args) // unknown overload: stop at current arity
        case Some((params, _, thisArgs, _)) =>
          thisArgs match
            case None => Some(args) // terminal: this ctor calls super (or nothing)
            case Some(ta) =>
              val subst: Map[String, BExpr] = params.map(_.name).zip(args).toMap
              def sub(e: BExpr): BExpr = BirTransform.mapExpr(e) {
                case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
                case x                                               => x
              }
              resolveThisChain(parentFqcn, ta.map(sub), depth + 1)
    }

  /** Raw transitive effects of constructing `parentFqcn(args)` — the matched
    * overload's body (params substituted) prefixed by its super/this chain, with
    * NO replayability guard. None only when structurally impossible (depth cap, or
    * a non-no-arg call into a type outside the translated set). */
  private def rawEffects(parentFqcn: String, args: List[BExpr], depth: Int): Option[List[BStmt]] =
    if depth > 8 then return None
    byFqcn.get(parentFqcn) match
      case None => if args.isEmpty then Some(Nil) else None
      case Some((_, info)) =>
        info.ctors.find(_._1.length == args.length) match
          case None => if args.isEmpty then Some(Nil) else None // implicit no-arg
          case Some((params, superArgs, thisArgs, body)) =>
            val subst: Map[String, BExpr] = params.map(_.name).zip(args).toMap
            def sub(e: BExpr): BExpr = BirTransform.mapExpr(e) {
              case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
              case x                                               => x
            }
            val ownBody = body.filterNot(_.k == BStmtK.Empty).map(BirTransform.mapStmt(_) {
              case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
              case x                                               => x
            })
            val upstream: Option[List[BStmt]] = thisArgs match
              case Some(ta) => rawEffects(parentFqcn, ta.map(sub), depth + 1)
              case None =>
                val sargs = superArgs.getOrElse(Nil).map(sub)
                info.superFqcn match
                  case Some(p) => rawEffects(p, sargs, depth + 1)
                  case None    => if sargs.isEmpty then Some(Nil) else None
            upstream.map(_ ++ ownBody)

  /** forFqcn: the subclass whose no-arg-primary plan replays these effects. Its
    * synthetic primary already calls `super()`, which runs the parent's OWN no-arg
    * construction path — so only the DELTA beyond that path needs replaying as
    * post-`this()` statements. Effects shared with no-arg construction (typically
    * the common `super(<constant>)` prefix that assigns private/final grandparent
    * fields) cancel out and never need to be replayable in the subclass. */
  def inlineSuperEffects(parentFqcn: String, args: List[BExpr], depth: Int = 0, forFqcn: String = ""): Option[List[BStmt]] =
    rawEffects(parentFqcn, args, depth) match
      case None => miss(s"$parentFqcn: effects not structurally computable at ${args.length} args")
      case Some(full) =>
        // subtract the parent no-arg path (run by the subclass primary's super())
        val base = rawEffects(parentFqcn, Nil, depth).getOrElse(Nil)
        val delta =
          if base.length <= full.length && full.take(base.length) == base then full.drop(base.length)
          else full
        // the delta executes in the SUBCLASS — its field assigns must resolve there
        // as visible + assignable (var, not private-at-declaration unless widened)
        if delta.nonEmpty && parentFqcn != forFqcn && !replayableFrom(forFqcn, delta) then
          miss(s"$parentFqcn: replay delta touches private/final members — not replayable in $forFqcn")
        else Some(delta)
