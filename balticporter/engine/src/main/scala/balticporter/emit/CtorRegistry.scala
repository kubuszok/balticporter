package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** Cross-unit constructor knowledge for the no-arg-primary + effect-replay funnel.
  * Tracks super-chain resolution, field widening, and replayability guards.
  */
final case class CtorInfo(
    superFqcn: Option[String],
    ctors: List[BCtor],
):
  def noArgCtor: Option[BCtor] = ctors.find(_.params.isEmpty)

  /** Resolve a super()/this() call by arity, preferring a type-matching overload. */
  def resolve(arity: Int, target: Option[List[BType]]): Option[BCtor] =
    val sameArity = ctors.filter(_.params.length == arity)
    target match
      case Some(ts) => sameArity.find(_.params.map(_.tpe) == ts).orElse(sameArity.headOption)
      case None     => sameArity.headOption

final class CtorRegistry(units: List[BUnit]):
  val byFqcn: Map[String, (BTypeDecl, CtorInfo)] =
    def entry(fqcn: String, t: BTypeDecl): List[(String, (BTypeDecl, CtorInfo))] =
      val info = CtorInfo(t.superClass.map(_.qname), t.ctors)
      // nested types register under Outer$Nested
      (fqcn -> (t, info)) :: (t.nested ++ t.inner).flatMap(n => entry(s"$fqcn$$${n.name}", n))
    units.flatMap { u =>
      u.types.flatMap { t =>
        entry(if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}", t)
      }
    }.toMap

  /** Resolve a registry key from package + simple name; falls back to unique `$Name` suffix. */
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

  /** Field names up the superclass chain (within closure); promoted ctor params
    * sharing these must be renamed to avoid shadowing. */
  def inheritedFieldNames(superFqcn: String, depth: Int = 0): Set[String] =
    if depth > 12 then return Set.empty
    keyOf(superFqcn).flatMap(byFqcn.get) match
      case None => Set.empty
      case Some((decl, info)) =>
        decl.fields.map(_.name).toSet ++ info.superFqcn.map(inheritedFieldNames(_, depth + 1)).getOrElse(Set.empty)

  /** Method names up the super chain (class + interfaces); locals sharing these must rename. */
  def inheritedMethodNames(fqcn: String, depth: Int = 0): Set[String] =
    if depth > 12 then return Set.empty
    keyOf(fqcn).flatMap(byFqcn.get) match
      case None => Set.empty
      case Some((decl, info)) =>
        val supers = info.superFqcn.toList ++ decl.interfaces.collect { case BType.Ref(q, _) => q }
        (decl.methods ++ decl.staticMethods).map(_.name).toSet ++
          supers.flatMap(s => inheritedMethodNames(s, depth + 1)).toSet

  /** Registry key of a superclass/interface reference. */
  private def keyOf(qname: String): Option[String] =
    val dollar = qname.replace('.', '$')
    if byFqcn.contains(qname) then Some(qname)
    else byFqcn.keysIterator.find(k => k == qname || k.replace('.', '$').endsWith(dollar))

  /** True when a supertype declares a nilary method `name`; the accessor cannot be
    * dropped because a `val` cannot satisfy a `def m(): T`. */
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

  /** Redundant accessors: field `f` + nilary method `f()` whose body is `return f`.
    * The method is dropped; calls rewrite to field reads. Keys: (fqcn, name).
    * Not collapsed when the method overrides an inherited nilary or is `@Override`. */
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

  /** Same-name field/method clashes where the method is `@Deprecated` and not a pure
    * accessor: keep the field, drop the deprecated method. */
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

  /** FQCNs whose no-arg construction path has no side effects (transitively). */
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
            info.noArgCtor.exists { c =>
              c.body.forall(_.k == BStmtK.Empty) && c.thisArgs.isEmpty &&
              c.superArgs.forall(_.isEmpty) && parentOk(info)
            }
          if ok then
            acc += fqcn
            changed = true
      }
    acc

  // --- super-effect inlining ---
  private val debug = sys.env.contains("BP_DEBUG")
  private def miss(why: String): Option[List[BStmt]] =
    if debug then System.err.println(s"[ctor-inline miss] $why")
    None

  /** Deterministic serialization for cache-key digesting (invalidates on ctor body edits). */
  lazy val digestInput: String =
    byFqcn.toList.sortBy(_._1).map { case (fqcn, (t, info)) =>
      val fields = t.fields.map(f => s"${f.name}:${f.mods.vis}:${f.mods.isFinal}").mkString(",")
      s"$fqcn|${info.superFqcn.getOrElse("")}|$fields|${info.ctors.toString}"
    }.mkString("\n")

  /** Private non-final parent fields assigned by subclass super-chain ctor effects;
    * widened to `protected var` so the effect replay can reach them. */
  lazy val widenedFields: Set[(String, String)] =
    val out = collection.mutable.Set[(String, String)]()
    def walk(rootFqcn: String, fqcn: String, args: List[BExpr], target: Option[List[BType]], depth: Int): Unit =
      if depth > 8 then return
      byFqcn.get(fqcn).foreach { case (pd, pi) =>
        pi.resolve(args.length, target).foreach { c =>
          if fqcn != rootFqcn then
            c.body.foreach { st =>
              st.k match
                case BStmtK.Assign(Ident(f, RefKind.OwnField), _, _)
                    if pd.fields.exists(fd => fd.name == f && fd.mods.vis == Vis.Private && !fd.mods.isFinal) =>
                  out += ((fqcn, f))
                case _ => ()
            }
          c.thisArgs match
            case Some(ta) => walk(rootFqcn, fqcn, ta, c.callTargetTypes, depth + 1)
            case None     => pi.superFqcn.foreach(p => walk(rootFqcn, p, c.superArgs.getOrElse(Nil), c.callTargetTypes, depth + 1))
        }
      }
    byFqcn.foreach { case (fqcn, (_, info)) =>
      info.ctors.foreach { c =>
        c.thisArgs match
          case Some(ta) => walk(fqcn, fqcn, ta, c.callTargetTypes, 0)
          case None     => info.superFqcn.foreach(p => walk(fqcn, p, c.superArgs.getOrElse(Nil), c.callTargetTypes, 0))
      }
    }
    out.toSet

  /** Whether `body` can legally execute in a subclass (fields assignable, members visible). */
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

  /** Follow `this()`-delegation chain to the terminal ctor, substituting args through.
    * Returns the args reaching the terminal. None when no matching ctor exists. */
  def resolveThisChain(parentFqcn: String, args: List[BExpr], depth: Int = 0, target: Option[List[BType]] = None): Option[List[BExpr]] =
    if depth > 12 then return None
    byFqcn.get(parentFqcn).flatMap { (_, info) =>
      info.resolve(args.length, target) match
        case None => if depth == 0 then None else Some(args) // unknown overload: stop at current arity
        case Some(c) =>
          c.thisArgs match
            case None => Some(args) // terminal: this ctor calls super (or nothing)
            case Some(ta) =>
              val subst: Map[String, BExpr] = c.params.map(_.name).zip(args).toMap
              def sub(e: BExpr): BExpr = BirTransform.mapExpr(e) {
                case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
                case x                                               => x
              }
              resolveThisChain(parentFqcn, ta.map(sub), depth + 1, c.callTargetTypes)
    }

  /** Raw transitive effects of constructing `parentFqcn(args)`, no replayability guard.
    * None when structurally impossible (depth cap, outside the translated set). */
  private def rawEffects(parentFqcn: String, args: List[BExpr], depth: Int, target: Option[List[BType]] = None): Option[List[BStmt]] =
    if depth > 8 then return None
    byFqcn.get(parentFqcn) match
      case None => if args.isEmpty then Some(Nil) else None
      case Some((_, info)) =>
        info.resolve(args.length, target) match
          case None => if args.isEmpty then Some(Nil) else None // implicit no-arg
          case Some(c) =>
            val subst: Map[String, BExpr] = c.params.map(_.name).zip(args).toMap
            def sub(e: BExpr): BExpr = BirTransform.mapExpr(e) {
              case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
              case x                                               => x
            }
            val ownBody = c.body.filterNot(_.k == BStmtK.Empty).map(BirTransform.mapStmt(_) {
              case Ident(n, RefKind.Param(_)) if subst.contains(n) => subst(n)
              case x                                               => x
            })
            val upstream: Option[List[BStmt]] = c.thisArgs match
              case Some(ta) => rawEffects(parentFqcn, ta.map(sub), depth + 1, c.callTargetTypes)
              case None =>
                val sargs = c.superArgs.getOrElse(Nil).map(sub)
                info.superFqcn match
                  case Some(p) => rawEffects(p, sargs, depth + 1, c.callTargetTypes)
                  case None    => if sargs.isEmpty then Some(Nil) else None
            upstream.map(_ ++ ownBody)

  /** Returns the DELTA beyond the parent's no-arg path (which the synthetic primary
    * already runs via super()). Guards replayability in `forFqcn`. */
  def inlineSuperEffects(parentFqcn: String, args: List[BExpr], depth: Int = 0, forFqcn: String = "", target: Option[List[BType]] = None): Option[List[BStmt]] =
    rawEffects(parentFqcn, args, depth, target) match
      case None => miss(s"$parentFqcn: effects not structurally computable at ${args.length} args")
      case Some(full) =>
        // subtract the parent no-arg path (already run by the subclass primary's super())
        val base = rawEffects(parentFqcn, Nil, depth).getOrElse(Nil)
        val delta =
          if base.length <= full.length && full.take(base.length) == base then full.drop(base.length)
          else full
        if delta.nonEmpty && parentFqcn != forFqcn && !replayableFrom(forFqcn, delta) then
          miss(s"$parentFqcn: replay delta touches private/final members — not replayable in $forFqcn")
        else Some(delta)
