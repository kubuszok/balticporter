package balticporter.transform

import balticporter.tir.*

/** Globals → implicits: a Java class whose `static` state is really an ambient CONTEXT
  * becomes an instance threaded through the program as a Scala 3 `using` parameter. The
  * sge/ssg case: a static config/registry reached from all over the code — made explicit
  * (and testable / swappable) without rewriting every call site by hand.
  *
  * A whole-program [[Phase]] (the `ResearchPlugin` case — it needs the CALL GRAPH before it
  * can rewrite):
  *   1. the context class `C` (chosen by `isContext`) has its `static` members turned into
  *      instance members;
  *   2. every method that TRANSITIVELY REACHES a static member of `C` — the seeds (methods
  *      that reference one directly) closed under `callersOf` — receives a `(using c: C)`
  *      clause;
  *   3. inside those methods, a `C.member` reference becomes `c.member`; and a call to
  *      another threaded method needs NO change — its `using` is satisfied automatically by
  *      the `c` now in scope (that is the whole point of `using` over an explicit param);
  *   4. a `given C = new C()` is synthesized in `C`'s companion so the boundary (a true
  *      entry point with no threaded caller) still resolves — an agent/author replaces it
  *      with the real entry-point context.
  *
  * Only methods OUTSIDE `C` are threaded; `C`'s own former-static methods just read their
  * now-instance members directly. The call graph is `callersOf`, itself built from the
  * Spoon-resolved usages — so "transitively reaches" is a real edge, not a guess.
  */
final class GlobalsToImplicitsTransform(isContext: Symbol => Boolean) extends Phase:
  def name = "globals->implicits"

  override def run(program: Program): Program =
    val ctxClass = program.symbols.all.find(s => isContext(s) && s.info.isInstanceOf[TypeRepr.TypeRef]).map(_.id)
    ctxClass match
      case None      => program
      case Some(cls) => rewrite(program, cls)

  private def rewrite(program: Program, cls: SymId): Program =
    given Program = program // the pre-rewrite program, for seed/closure queries
    val ctxRef  = TypeRepr.TypeRef(TypeRepr.NoType, cls)
    val ctxName = program.symbolOf(cls).map(_.name).getOrElse("Ctx")

    val staticMembers = program.symbols.all.filter(s => s.owner == cls && s.flags.isStatic).map(_.id).toSet
    if staticMembers.isEmpty then return program

    def isMethod(s: SymId): Boolean = program.symbolOf(s).exists(_.info.isInstanceOf[TypeRepr.MethodType])
    def ownedByCtx(s: SymId): Boolean = program.symbolOf(s).exists(_.owner == cls)

    // seeds: methods (not C's own) that directly reference a static member.
    val seeds = staticMembers.flatMap(m => program.usages(m).map(_.enclosing))
      .filter(e => e != SymId.None && isMethod(e) && !ownedByCtx(e))
    // threaded = seeds closed under callersOf (methods that must forward the context).
    val threaded = closure(seeds, cls)
    if threaded.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, flags: Flags, owner: SymId, info: TypeRepr): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, name, flags, owner, info)
      id
    val ctxParam: Map[SymId, SymId] =
      threaded.iterator.map(m => m -> mint("ctx", Flags(isParam = true, isGiven = true), m, ctxRef)).toMap
    val givenSym = mint(s"${ctxName}Ctx", Flags(isStatic = true, isGiven = true), cls, ctxRef)

    // DECISION PROVENANCE: one row per DECLARATION whose emitted signature moved — the threaded
    // methods, which gain a `(using ctx: C)` clause, and C's own statics, which stop being statics.
    // Both are declaration-level already. Nothing is recorded for a CALL into a threaded method:
    // its argument is supplied by the `using` in scope, so no call site changed at all — which is
    // the whole reason `using` was chosen over an explicit parameter.
    //
    // `Reason.Configured`, keyed by the context class. The mechanism is general and WHICH class is
    // an ambient context (rather than a bag of unrelated statics) is a fact about one library,
    // supplied as the `isContext` predicate — so the key an agent acts on is the class it selected.
    val ctxFqn = program.symbolOf(cls).map(_.fullName).getOrElse(ctxName)
    threaded.foreach { m =>
      program.symbolOf(m).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.RetypedSignature,
          subject    = m,
          subjectFqn = s.fullName,
          detail = Map(
            "from" -> "reaches C's static state directly",
            "to"   -> s"takes `(using ctx: $ctxName)`",
            "key"  -> ctxFqn,
            "why"  -> ("this method transitively reaches a static member of the context class, so " +
              "the ambient state it read is threaded to it explicitly"),
          ),
          reason = Reason.Configured(name, ctxFqn),
          origin = Decision.originOf(program, s.id),
        ))
      }
    }
    staticMembers.foreach { m =>
      program.symbolOf(m).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.RetypedSignature,
          subject    = m,
          subjectFqn = s.fullName,
          detail = Map(
            "from" -> "java `static` member of the context class",
            "to"   -> "an instance member, reached through the threaded context",
            "key"  -> ctxFqn,
            "why"  -> "the class's static state IS the context, so it becomes that context's state",
          ),
          reason = Reason.Configured(name, ctxFqn),
          origin = Decision.originOf(program, s.id),
        ))
      }
    }

    // de-static C's members (they become instance state/methods); keep everything else.
    val symbols0 = program.symbols.all.map(s =>
      if staticMembers(s.id) then s.copy(flags = s.flags.copy(isStatic = false)) else s)
    val symbols = SymbolTable(symbols0 ++ minted)
    val prog2   = program.rebuilt(symbols = symbols) // post-mint, for the rewrite

    // the boundary given lives in C's companion (static member of C → emitter puts it there).
    val o = Origin.synthetic
    val givenVal = Tree.ValDef(givenSym, TypeTree(ctxRef, o), Some(Tree.New(TypeTree(ctxRef, o), ctxRef, o)), o)

    val units = program.units.map(u =>
      rewriteClass(u, cls, staticMembers, threaded, ctxParam, ctxRef, givenVal)(using prog2))
    program.rebuilt(units, symbols)

  private val minted = collection.mutable.ListBuffer[Symbol]()

  /** seed methods closed under `callersOf`, excluding C's own methods. */
  private def closure(seeds: Set[SymId], cls: SymId)(using p: Program): Set[SymId] =
    val out = collection.mutable.Set.from(seeds)
    val wl  = collection.mutable.Queue.from(seeds)
    while wl.nonEmpty do
      val m = wl.dequeue()
      for c <- p.callersOf(m) if !p.symbolOf(c).exists(_.owner == cls) && !out(c) do
        out += c; wl += c
    out.toSet

  private def rewriteClass(cd: Tree.ClassDef, cls: SymId, statics: Set[SymId], threaded: Set[SymId],
      ctxParam: Map[SymId, SymId], ctxRef: TypeRepr, givenVal: Tree.ValDef)(using Program): Tree.ClassDef =
    val body = cd.body.map {
      case d: Tree.DefDef if threaded(d.symbol) =>
        // a method reached from outside C: add `(using ctx: C)`, rewrite `C.member` → `ctx.member`.
        val ctx = ctxParam(d.symbol)
        val rw  = new StaticToRecv(statics, o => Tree.Ident(ctx, ctxRef, o))
        d.copy(
          paramss = d.paramss :+ List(Tree.ValDef(ctx, TypeTree(ctxRef, d.origin), None, d.origin)),
          rhs = d.rhs.map(r => StandardTraversal.mapTerm(rw, r)),
        )
      case d: Tree.DefDef if cd.symbol == cls =>
        // C's OWN (former-static) method: its members are now instance state → `C.member` → `this.member`.
        val rw = new StaticToRecv(statics, o => Tree.This(cls, ctxRef, o))
        d.copy(rhs = d.rhs.map(r => StandardTraversal.mapTerm(rw, r)))
      case c: Tree.ClassDef => rewriteClass(c, cls, statics, threaded, ctxParam, ctxRef, givenVal)
      case other            => other
    }
    // append the boundary given to C's own body (a static member → companion object).
    if cd.symbol == cls then cd.copy(body = body :+ givenVal) else cd.copy(body = body)

  /** a per-method sub-phase: rewrite a reference to a now-instance static member into
    * `<receiver>.member`. Reusing the standard traversal — the `Ident`/`Select` hooks catch
    * the static-member reference wherever it occurs, including as a call's `fun`. The receiver
    * is the threaded `using ctx` outside C, or `this` inside C. */
  private final class StaticToRecv(statics: Set[SymId], receiver: Origin => Term) extends Phase:
    def name = "static->recv"
    private def sel(member: SymId, tpe: TypeRepr, o: Origin): Term = Tree.Select(receiver(o), member, tpe, o)
    override def transformIdent(t: Tree.Ident)(using Program): Term =
      if statics(t.sym) then sel(t.sym, t.tpe, t.origin) else t
    override def transformSelect(t: Tree.Select)(using Program): Term =
      if statics(t.sym) then sel(t.sym, t.tpe, t.origin) else t
