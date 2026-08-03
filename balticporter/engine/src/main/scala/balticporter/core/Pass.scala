package balticporter.core

import balticporter.core.BExpr.*

/** Tier-3 rule API (DESIGN.md §2.4, cut to the current engine surface): pure
  * BUnit → BUnit passes, single-concern, individually versioned. Order is
  * explicit — the port program lists its passes; the framework never reorders.
  * `id@version` of every registered pass joins the action-cache fingerprint so
  * a rule change invalidates exactly like an engine change.
  */
trait BirPass:
  /** stable id, e.g. "vocab/apply" or "ssg/package-rename". */
  def id: String
  /** bumped on ANY behavior change — this is a cache-correctness contract. */
  def version: Int
  def run(unit: BUnit): BUnit

object PassPipeline:
  /** joins the action-cache key (empty when no passes are registered). */
  def fingerprint(passes: List[BirPass]): String =
    passes.map(p => s"${p.id}@${p.version}").mkString(";")

  def run(passes: List[BirPass], unit: BUnit): BUnit =
    passes.foldLeft(unit)((u, p) => p.run(u))

/** Rewrites every qualified-name occurrence in a unit — the substrate for
  * Tier-2 type mappings and package renames. Covers type positions (decls,
  * locals, catches, params, returns, tparams) and the qname-carrying
  * expression fields (call owners, static receivers/fields, class literals,
  * method/ctor references).
  */
object QNameMap:

  def apply(u: BUnit)(f: String => String): BUnit =
    u.copy(types = u.types.map(mapDecl(_)(f)))

  private def mt(t: BType)(f: String => String): BType = t match
    case BType.Ref(q, as) => BType.Ref(f(q), as.map(mt(_)(f)))
    case BType.Arr(e)     => BType.Arr(mt(e)(f))
    case BType.Wild(a, b) => BType.Wild(a.map(mt(_)(f)), b.map(mt(_)(f)))
    case other            => other

  private def mtr(t: BType.Ref)(f: String => String): BType.Ref =
    BType.Ref(f(t.qname), t.args.map(mt(_)(f)))

  private def exprF(f: String => String): BExpr => BExpr =
    def t(x: BType) = mt(x)(f)
    {
      case Call(recv, n, args, formals, ownerQ) =>
        val recv2 = recv match
          case Recv.Static(o) => Recv.Static(f(o))
          case r              => r
        Call(recv2, n, args, formals.map(_.map(fm => fm.copy(tpe = t(fm.tpe)))), ownerQ.map(f))
      case Ident(n, RefKind.StaticField(o))  => Ident(n, RefKind.StaticField(f(o)))
      case n: New =>
        n.copy(
          tpe = mtr(n.tpe)(f),
          formals = n.formals.map(_.map(fm => fm.copy(tpe = t(fm.tpe)))),
          anonBody = n.anonBody.map(b =>
            b.copy(
              fields = b.fields.map(fl => fl.copy(tpe = t(fl.tpe))),
              methods = b.methods.map(fixMethod(_)(f)),
              init = b.init.map(fixStmt(_)(f)),
            )
          ),
        )
      case NewArray(el, d, i)                => NewArray(t(el), d, i)
      case Cast(tp, e)                       => Cast(t(tp), e)
      case InstanceOf(e, tp)                 => InstanceOf(e, t(tp))
      case ClassLit(tp)                      => ClassLit(t(tp))
      case Typed(e, tp)                      => Typed(e, t(tp))
      case MethodRef(Left(owner), n)         => MethodRef(Left(f(owner)), n)
      case CtorRef(tp, fo)                   => CtorRef(mtr(tp)(f), fo.map(t))
      case UnboundMethodRef(rt, n, fo)       => UnboundMethodRef(t(rt), n, fo.map(t))
      case e                                 => e
    }

  /** second walk: BType fields on statements that the expression mapper can't see. */
  private def fixStmt(s: BStmt)(f: String => String): BStmt =
    def fs(x: BStmt) = fixStmt(x)(f)
    val k = s.k match
      case BStmtK.LocalVar(n, tp, i, ef) => BStmtK.LocalVar(n, mt(tp)(f), i, ef)
      case BStmtK.If(c, a, b)            => BStmtK.If(c, a.map(fs), b.map(_.map(fs)))
      case BStmtK.While(c, b)            => BStmtK.While(c, b.map(fs))
      case BStmtK.DoWhile(b, c)          => BStmtK.DoWhile(b.map(fs), c)
      case BStmtK.Block(b)               => BStmtK.Block(b.map(fs))
      case BStmtK.Try(b, cs, fin) =>
        BStmtK.Try(
          b.map(fs),
          cs.map(c => BCatch(c.param, c.types.map(mt(_)(f)), c.body.map(fs))),
          fin.map(_.map(fs)),
        )
      case BStmtK.Boundary(b, l)     => BStmtK.Boundary(b.map(fs), l)
      case BStmtK.Match(scr, cases)  => BStmtK.Match(scr, cases.map(c => c.copy(body = c.body.map(fs))))
      case BStmtK.Synchronized(l, b) => BStmtK.Synchronized(l, b.map(fs))
      case BStmtK.LocalType(t)       => BStmtK.LocalType(mapDecl(t)(f))
      case other                     => other
    s.copy(k = k)

  private def fixMethod(m: BMethod)(f: String => String): BMethod =
    m.copy(
      tparams = m.tparams.map(tp => tp.copy(upper = tp.upper.map(mt(_)(f)))),
      params = m.params.map(p => p.copy(tpe = mt(p.tpe)(f))),
      ret = mt(m.ret)(f),
      body = m.body.map(_.map(fixStmt(_)(f))),
    )

  private def mapDecl(t: BTypeDecl)(f: String => String): BTypeDecl =
    val e = BirTransform.mapTypeDecl(t)(exprF(f))
    e.copy(
      tparams = e.tparams.map(tp => tp.copy(upper = tp.upper.map(mt(_)(f)))),
      superClass = e.superClass.map(mtr(_)(f)),
      interfaces = e.interfaces.map(mt(_)(f)),
      fields = e.fields.map(fl => fl.copy(tpe = mt(fl.tpe)(f))),
      staticFields = e.staticFields.map(fl => fl.copy(tpe = mt(fl.tpe)(f))),
      ctors = e.ctors.map(c => c.copy(params = c.params.map(p => p.copy(tpe = mt(p.tpe)(f))), body = c.body.map(fixStmt(_)(f)))),
      methods = e.methods.map(fixMethod(_)(f)),
      staticMethods = e.staticMethods.map(fixMethod(_)(f)),
      staticInit = e.staticInit.map(fixStmt(_)(f)),
      instanceInit = e.instanceInit.map(fixStmt(_)(f)),
      nested = e.nested.map(mapDecl(_)(f)),
      inner = e.inner.map(mapDecl(_)(f)),
    )
