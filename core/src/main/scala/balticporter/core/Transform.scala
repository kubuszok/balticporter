package balticporter.core

import balticporter.core.BExpr.*

/** Structural bottom-up mappers over BIR — the substrate passes are built on. */
object BirTransform:

  def mapExpr(e: BExpr)(f: BExpr => BExpr): BExpr =
    def m(x: BExpr): BExpr = mapExpr(x)(f)
    val rebuilt = e match
      case _: Lit | _: Ident | This | _: ClassLit | _: UnboundMethodRef | _: CtorRef => e
      case Select(r, n)       => Select(m(r), n)
      case AssignExpr(l, r)   => AssignExpr(m(l), m(r))
      case IncDecExpr(t, op, post) => IncDecExpr(m(t), op, post)
      case ArrayLength(a)     => ArrayLength(m(a))
      case ArrayAccess(a, i)  => ArrayAccess(m(a), m(i))
      case c: Call =>
        val recv = c.recv match
          case Recv.On(r) => Recv.On(m(r))
          case other      => other
        c.copy(recv = recv, args = c.args.map(m))
      case n: New =>
        val body = n.anonBody.map(b =>
          b.copy(
            fields = b.fields.map(fl => fl.copy(init = fl.init.map(m))),
            methods = b.methods.map(mapMethod(_)(f)),
            init = b.init.map(mapStmt(_)(f)),
          )
        )
        n.copy(args = n.args.map(m), anonBody = body)
      case NewArray(el, d, i) => NewArray(el, d.map(m), i.map(_.map(m)))
      case Binary(op, l, r, c) => Binary(op, m(l), m(r), c)
      case Unary(op, x, p)     => Unary(op, m(x), p)
      case Ternary(c, t, e2)   => Ternary(m(c), m(t), m(e2))
      case Cast(t, x)          => Cast(t, m(x))
      case InstanceOf(x, t)    => InstanceOf(m(x), t)
      case Typed(x, t)         => Typed(m(x), t)
      case l: Lambda           => l.copy(body = l.body.left.map(_.map(mapStmt(_)(f))).map(m))
      case mr: MethodRef       => mr.copy(prefix = mr.prefix.map(m))
    f(rebuilt)

  def mapStmt(s: BStmt)(f: BExpr => BExpr): BStmt =
    def me(x: BExpr): BExpr = mapExpr(x)(f)
    def ms(x: BStmt): BStmt = mapStmt(x)(f)
    val k = s.k match
      case BStmtK.LocalVar(n, t, i, ef) => BStmtK.LocalVar(n, t, i.map(me), ef)
      case BStmtK.ExprStmt(e)           => BStmtK.ExprStmt(me(e))
      case BStmtK.Assign(l, r, op)      => BStmtK.Assign(me(l), me(r), op)
      case BStmtK.If(c, t, e)           => BStmtK.If(me(c), t.map(ms), e.map(_.map(ms)))
      case BStmtK.Return(e)             => BStmtK.Return(e.map(me))
      case BStmtK.Throw(e)              => BStmtK.Throw(me(e))
      case BStmtK.While(c, b)           => BStmtK.While(me(c), b.map(ms))
      case BStmtK.DoWhile(b, c)         => BStmtK.DoWhile(b.map(ms), me(c))
      case BStmtK.Assert(c, m2)         => BStmtK.Assert(me(c), m2.map(me))
      case BStmtK.Block(b)              => BStmtK.Block(b.map(ms))
      case BStmtK.Try(b, cs, fin) =>
        BStmtK.Try(b.map(ms), cs.map(c => c.copy(body = c.body.map(ms))), fin.map(_.map(ms)))
      case BStmtK.Boundary(b, l)        => BStmtK.Boundary(b.map(ms), l)
      case BStmtK.Match(scr, cases)     => BStmtK.Match(me(scr), cases.map(c => c.copy(exprs = c.exprs.map(me), body = c.body.map(ms))))
      case BStmtK.Synchronized(l, b)    => BStmtK.Synchronized(me(l), b.map(ms))
      case BStmtK.LocalType(t)          => BStmtK.LocalType(mapTypeDecl(t)(f))
      case BStmtK.Empty | _: BStmtK.LoopBreak => s.k
    s.copy(k = k)

  def mapMethod(m: BMethod)(f: BExpr => BExpr): BMethod =
    m.copy(body = m.body.map(_.map(mapStmt(_)(f))))

  def mapCtor(c: BCtor)(f: BExpr => BExpr): BCtor =
    c.copy(
      superArgs = c.superArgs.map(_.map(mapExpr(_)(f))),
      thisArgs = c.thisArgs.map(_.map(mapExpr(_)(f))),
      body = c.body.map(mapStmt(_)(f)),
    )

  /** Maps every expression in a type declaration (recursing into nested types). */
  def mapTypeDecl(t: BTypeDecl)(f: BExpr => BExpr): BTypeDecl =
    t.copy(
      fields = t.fields.map(fl => fl.copy(init = fl.init.map(mapExpr(_)(f)))),
      ctors = t.ctors.map(mapCtor(_)(f)),
      methods = t.methods.map(mapMethod(_)(f)),
      staticFields = t.staticFields.map(fl => fl.copy(init = fl.init.map(mapExpr(_)(f)))),
      staticMethods = t.staticMethods.map(mapMethod(_)(f)),
      enumCases = t.enumCases.map(c => c.copy(args = c.args.map(mapExpr(_)(f)))),
      staticInit = t.staticInit.map(mapStmt(_)(f)),
      instanceInit = t.instanceInit.map(mapStmt(_)(f)),
      nested = t.nested.map(mapTypeDecl(_)(f)),
    )
