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
  *
  * KNOWN LIMIT, out of this transform's shape: a LAMBDA's own parameter. Java lets one be
  * reassigned (`(x) -> { x = x + 1; return x; }` compiles); Scala's function parameters are `val`,
  * so the emitted lambda does not. This pass rewrites `DefDef.paramss` and a `Tree.Lambda` has no
  * `DefDef`, so nothing here reaches it. It degrades loudly — the emitted `x = x + 1` is a compile
  * error at the port, not a behavioural difference — and fixing it means giving `Tree.Lambda` the
  * same param-slot rewrite, not widening the scan.
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

    def scanDef(d: Tree.DefDef)(using Program): Unit =
      val params = d.paramss.flatten.map(_.symbol).toSet
      val written = d.rhs.map(reassignedIn(_, params)).getOrElse(Set.empty)
      written.foreach { p =>
        program.symbolOf(p).foreach { s => argOf(p) = mint(s); nowVar += p }
      }
    // walk with the STANDARD traversal: it reaches every `DefDef` in the tree, INCLUDING the
    // methods of an anonymous class (`new ClickListener() { … }`), which a hand-rolled recursion
    // over class bodies alone does not — and libGDX reassigns parameters inside listener bodies
    // constantly (`ScrollPane`'s `deltaX = 0`, `Interpolation`'s `a = a * 2`).
    locally {
      given Program = program
      val scan = new Phase:
        def name = "reassigned-params->var/scan"
        override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { scanDef(d); d }
      program.units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    }
    if argOf.isEmpty then return program

    // param symbol becomes a mutable local (same name/id → references follow); fresh arg symbol
    // (isParam) takes the slot. Drop isParam/isMutable bookkeeping accordingly.
    val symbols0 = program.symbols.all.map { s =>
      if nowVar(s.id) then s.copy(flags = s.flags.copy(isParam = false, isMutable = true)) else s
    }
    // DECISION PROVENANCE: one row per METHOD whose parameter SLOTS moved — never one per
    // parameter, since a method has ONE emitted signature and that is what an agent is reading.
    // The parameter symbol keeps its name and becomes the `var`; a fresh `name$arg` takes the
    // slot, so the emitted signature really does differ from java's and nothing else in the port
    // says why. Universal: java lets a method reassign its parameters and scala's are `val`, which
    // is true of every codebase and takes no policy.
    argOf.keys.toList
      .flatMap(p => program.symbolOf(p).map(s => s.owner -> s.name))
      .groupBy(_._1)
      .foreach { (owner, ps) =>
        program.symbolOf(owner).foreach { m =>
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = owner,
            subjectFqn = m.fullName,
            detail = Map(
              "params" -> ps.map(_._2).distinct.sorted.mkString(", "),
              "from"   -> "java parameters, reassigned in the body",
              "to"     -> "`<name>$arg` parameter slots, with a leading `var <name> = <name>$arg`",
              "why"    -> ("java lets a method reassign its parameters; scala's are `val`, so the " +
                "reassignment would not compile and every body reference has to bind to the var"),
            ),
            reason = Reason.Universal("reassigned-param-to-var"),
            origin = Decision.originOf(program, owner),
          ))
        }
      }

    val symbols = SymbolTable(symbols0 ++ minted)
    given Program = program.rebuilt(symbols = symbols)
    val rewrite = new Phase:
      def name = "reassigned-params->var/rewrite"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = rewriteDef(d)
    val units = program.units.map(u => StandardTraversal.mapClassDef(rewrite, u))
    program.rebuilt(units, symbols)

  private def rewriteDef(d: Tree.DefDef)(using Program): Tree.DefDef =
    val shadows = d.paramss.flatten.filter(v => argOf.contains(v.symbol))
    if shadows.isEmpty then return d
    val o = d.origin
    // rename the parameter slots to their `$arg` symbols
    val paramss2 = d.paramss.map(_.map(v => argOf.get(v.symbol).map(a => v.copy(symbol = a)).getOrElse(v)))
    // prepend `var name: T = name$arg` for each shadowed parameter
    val prelude: List[Statement] = shadows.map(v =>
      Tree.ValDef(v.symbol, v.tpt, Some(Tree.Ident(argOf(v.symbol), v.tpt.tpe, o)), o))
    val isCtor = summon[Program].symbolOf(d.symbol).exists(_.name == "<init>")
    val body = d.rhs match
      // `copy`, never a fresh `Tree.Block`: the block carries end-of-body trivia, and a rebuild
      // from its parts silently drops whatever field the rebuild does not name.
      case Some(b @ Tree.Block(stats, _, _, _, _)) =>
        // a constructor body must LEAD with `this(...)`/`super(...)` delegation — insert the
        // `var` shadows right AFTER it, not before.
        stats match
          case (deleg @ Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) :: rest
              if isCtor && summon[Program].symbolOf(m).exists(_.name == "<init>") =>
            b.copy(stats = deleg :: (prelude ++ rest))
          case _ => b.copy(stats = prelude ++ stats)
      case Some(other) => Tree.Block(prelude, other, other.tpe, o)
      case None        => Tree.Block(prelude, Tree.Literal(Constant.UnitC, TypeRepr.NoType, o), TypeRepr.NoType, o)
    d.copy(paramss = paramss2, rhs = Some(body))

  /** Parameters (from `params`) that are the target of an assignment anywhere in `t`.
    *
    * Scanned with [[StandardTraversal.scanTerm]] rather than a private recursion. The recursion
    * this replaced enumerated node kinds by hand and had gone stale in the ways such a list always
    * does — no `NewArray` case, so `new int[]{ p++ }` and `new int[p++]` were not seen; no
    * `Repeated`, so a vararg argument `f(p++)` was not seen; and, worst because it is ordinary
    * Java, `Block.stats` was filtered to `case x: Term`, which DROPS every `ValDef` — so the
    * initialiser of a local (`int c = s.charAt(i++)`) was not scanned at all.
    *
    * All three degrade loudly rather than silently: the parameter stays a `val`, the emitted
    * reassignment does not compile, and the port's error count says so. That is why this is
    * hardening and not a bug fix — but "loud" is only true while somebody is reading the count,
    * and the node list would have gone stale again at the next `Tree` case added. Missing a node
    * kind is now impossible by construction; the map traversal is total over `Term`.
    *
    * Scanning MORE of the tree cannot produce a false positive: `params` holds this method's own
    * parameter symbols, and a symbol identifies its binder uniquely. The two node kinds the scan
    * newly descends into — a `Tree.Lambda` body and a `Tree.New`'s anonymous-class body — cannot
    * assign an ENCLOSING method's parameter in legal Java at all, which is why the old recursion
    * got away with returning `Nil` for both. javac, on `Runnable lam(int p) { return () -> { p = p
    * + 1; }; }`, says "local variables referenced from a lambda expression must be final or
    * effectively final", and the same for an inner class. So those two cases are genuinely
    * unreachable HERE and no test pins them; what an anonymous method does to its OWN parameters
    * is reached by the `DefDef` walk in [[run]] instead, and is pinned there.
    */
  private def reassignedIn(t: Term, params: Set[SymId])(using Program): Set[SymId] =
    StandardTraversal.scanTerm(t, Set.empty[SymId]) { (found, x) =>
      x match
        case Tree.Assign(Tree.Ident(s, _, _), _, _, _) if params(s)   => found + s
        case Tree.IncDec(Tree.Ident(s, _, _), _, _, _, _) if params(s) => found + s // `p++`/`p--`
        case _                                                         => found
    }
