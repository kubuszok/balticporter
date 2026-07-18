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
    units.flatMap { u =>
      u.types.map { t =>
        val fqcn = if u.pkg.isEmpty then t.name else s"${u.pkg}.${t.name}"
        val info = CtorInfo(
          t.superClass.map(_.qname),
          t.ctors.map(c => (c.params, c.superArgs, c.thisArgs, c.body)),
        )
        fqcn -> (t, info)
      }
    }.toMap

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

  def inlineSuperEffects(parentFqcn: String, args: List[BExpr], depth: Int = 0): Option[List[BStmt]] =
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
            val upstream: Option[List[BStmt]] = thisArgs match
              case Some(ta) => inlineSuperEffects(parentFqcn, ta.map(sub), depth + 1)
              case None =>
                val sargs = superArgs.getOrElse(Nil).map(sub)
                info.superFqcn match
                  case Some(p) => inlineSuperEffects(p, sargs, depth + 1)
                  case None    => if sargs.isEmpty then Some(Nil) else miss(s"$parentFqcn: super args with no superclass")
            upstream.map(_ ++ ownBody)
