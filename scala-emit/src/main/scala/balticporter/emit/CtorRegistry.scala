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

  /** Java-style redundant accessors: a field `f` plus a nilary same-name method
    * whose body is exactly `return f`. Scala can't declare both — the method is
    * DROPPED and every resolved nilary call `x.f()` rewrites to the field read
    * (semantically exact: the method returned the field). Keys are ($-qualified
    * fqcn, member name). */
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
      val inst = t.methods.collect {
        case m if m.params.isEmpty && t.fields.exists(_.name == m.name) &&
          returnsField(m.body, m.name, static = false) => (fqcn, m.name)
      }
      val stat = t.staticMethods.collect {
        case m if m.params.isEmpty && t.staticFields.exists(_.name == m.name) &&
          returnsField(m.body, m.name, static = true) => (fqcn, m.name)
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

  /** forFqcn: the subclass whose plan replays the effects — accessibility/finality
    * checks are skipped at levels equal to it (its own plan un-finals what it
    * assigns; only CROSS-class replay needs visible assignable members). */
  def inlineSuperEffects(parentFqcn: String, args: List[BExpr], depth: Int = 0, forFqcn: String = ""): Option[List[BStmt]] =
    if depth > 8 then return miss(s"depth cap at $parentFqcn")
    byFqcn.get(parentFqcn) match
      case None =>
        // outside the translated set: only an effect-free no-arg call is replayable
        if args.isEmpty then Some(Nil) else miss(s"$parentFqcn outside set with ${args.length} args")
      case Some((_, info)) =>
        info.ctors.find(_._1.length == args.length) match
          case None => if args.isEmpty then Some(Nil) else miss(s"$parentFqcn: no ${args.length}-arity ctor") // implicit no-arg
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
            // the inlined statements execute in the SUBCLASS — references that were
            // legal in the parent's own ctor may not be there: private members are
            // invisible, and final fields emit as vals (not assignable post-hoc)
            if parentFqcn != forFqcn && !replayableFrom(parentFqcn, ownBody) then
              return miss(s"$parentFqcn: ctor effects touch private/final members — not replayable in a subclass")
            val upstream: Option[List[BStmt]] = thisArgs match
              case Some(ta) => inlineSuperEffects(parentFqcn, ta.map(sub), depth + 1, forFqcn)
              case None =>
                val sargs = superArgs.getOrElse(Nil).map(sub)
                info.superFqcn match
                  case Some(p) => inlineSuperEffects(p, sargs, depth + 1, forFqcn)
                  case None    => if sargs.isEmpty then Some(Nil) else miss(s"$parentFqcn: super args with no superclass")
            upstream.map(_ ++ ownBody)
