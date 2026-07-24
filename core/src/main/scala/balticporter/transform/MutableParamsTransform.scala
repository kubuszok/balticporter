package balticporter.transform

import balticporter.tir.*

/** Java lets a method reassign its parameters; Scala parameters are `val`. For each parameter
  * written to in its method body, rename the PARAMETER to `name$arg` and prepend a mutable
  * local `var name: T = name$arg` — so every existing body reference (which resolves by SymId
  * to the parameter) now binds to the `var`, and the reassignment type-checks.
  *
  * A structural Java→Scala transform, symbol-driven: the parameter symbol is repurposed as the
  * local `var` (keeping its name and all its references), and a fresh symbol takes the actual
  * parameter slot. No reference rewriting is needed — identity does the work.
  */
final class MutableParamsTransform extends Phase:
  def name = "reassigned-params->var"

  private val minted = collection.mutable.ListBuffer[Symbol]()
  // param SymId → fresh arg SymId, for every parameter reassigned somewhere in its method.
  private val argOf  = collection.mutable.Map[SymId, SymId]()
  private val nowVar = collection.mutable.Set[SymId]()

  override def run(program: Program): Program =
    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(s: Symbol): SymId =
      val id = SymId(next); next += 1
      minted += s.copy(id = id, name = s.name + "$arg", fullName = s.fullName + "$arg")
      id

    def scanDef(d: Tree.DefDef): Unit =
      // skip constructors: their body must lead with `this(...)`/`super(...)` delegation, which a
      // prepended `var` would displace (and Scala ctor params are rarely reassigned anyway).
      if program.symbolOf(d.symbol).exists(_.name == "<init>") then return
      val params = d.paramss.flatten.map(_.symbol).toSet
      val written = d.rhs.map(reassignedIn(_, params)).getOrElse(Set.empty)
      written.foreach { p =>
        program.symbolOf(p).foreach { s => argOf(p) = mint(s); nowVar += p }
      }
    program.units.foreach(_.body.foreach(collectDefs(_, scanDef)))
    if argOf.isEmpty then return program

    // param symbol becomes a mutable local (same name/id → references follow); fresh arg symbol
    // (isParam) takes the slot. Drop isParam/isMutable bookkeeping accordingly.
    val symbols0 = program.symbols.all.map { s =>
      if nowVar(s.id) then s.copy(flags = s.flags.copy(isParam = false, isMutable = true)) else s
    }
    val symbols = SymbolTable(symbols0 ++ minted)
    given Program = new Program(program.units, symbols, program.xref)
    val units = program.units.map(u => rewriteClass(u))
    new Program(units, symbols, program.xref)

  private def rewriteClass(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
    cd.copy(body = cd.body.map {
      case d: Tree.DefDef   => rewriteDef(d)
      case c: Tree.ClassDef => rewriteClass(c)
      case other            => other
    })

  private def rewriteDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    val shadows = d.paramss.flatten.filter(v => argOf.contains(v.symbol))
    if shadows.isEmpty then return d
    val o = d.origin
    // rename the parameter slots to their `$arg` symbols
    val paramss2 = d.paramss.map(_.map(v => argOf.get(v.symbol).map(a => v.copy(symbol = a)).getOrElse(v)))
    // prepend `var name: T = name$arg` for each shadowed parameter
    val prelude: List[Statement] = shadows.map(v =>
      Tree.ValDef(v.symbol, v.tpt, Some(Tree.Ident(argOf(v.symbol), v.tpt.tpe, o)), o))
    val body = d.rhs match
      case Some(Tree.Block(stats, expr, tp, bo)) => Tree.Block(prelude ++ stats, expr, tp, bo)
      case Some(other)                           => Tree.Block(prelude, other, other.tpe, o)
      case None                                  => Tree.Block(prelude, Tree.Literal(Constant.UnitC, TypeRepr.NoType, o), TypeRepr.NoType, o)
    d.copy(paramss = paramss2, rhs = Some(body))

  private def collectDefs(s: Statement, f: Tree.DefDef => Unit): Unit = s match
    case d: Tree.DefDef   => f(d); d.rhs.foreach(collectTermDefs(_, f))
    case c: Tree.ClassDef => c.body.foreach(collectDefs(_, f))
    case _                => ()
  private def collectTermDefs(t: Term, f: Tree.DefDef => Unit): Unit = () // nested local defs: none in our model

  /** parameters (from `params`) that are the target of an assignment anywhere in `t`. */
  private def reassignedIn(t: Term, params: Set[SymId]): Set[SymId] =
    val found = collection.mutable.Set[SymId]()
    def walk(x: Term): Unit =
      x match
        case Tree.Assign(Tree.Ident(s, _, _), rhs, _, _) => if params(s) then found += s; walk(rhs)
        case _ => ()
      subterms(x).foreach(walk)
    walk(t)
    found.toSet

  private def subterms(t: Term): List[Term] = t match
    case Tree.Block(stats, e, _, _)     => stats.collect { case x: Term => x } :+ e
    case Tree.If(c, a, b, _, _)         => List(c, a, b)
    case Tree.Apply(fun, args, _, _, _) => fun :: args
    case Tree.Assign(l, r, _, _)        => List(l, r)
    case Tree.While(c, b, _, _)         => List(c, b)
    case Tree.DoWhile(b, c, _, _)       => List(b, c)
    case Tree.For(init, c, u, b, _, _)  => init.collect { case x: Term => x } ++ c.toList ++ u.collect { case x: Term => x } :+ b
    case Tree.ForEach(_, it, b, _, _)   => List(it, b)
    case Tree.Try(_, b, cs, fin, _, _)  => (b :: cs.map(_.body)) ++ fin.toList
    case Tree.Match(scr, cs, _, _)      => scr :: cs.map(_.body)
    case Tree.Select(q, _, _, _)        => List(q)
    case Tree.Return(e, _, _)           => e.toList
    case Tree.Throw(e, _, _)            => List(e)
    case Tree.Synchronized(l, b, _, _)  => List(l, b)
    case Tree.Typed(e, _, _, _)         => List(e)
    case _                              => Nil
