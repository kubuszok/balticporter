package balticporter.transform

import balticporter.tir.*

/** Java lets a method reassign its parameters; Scala parameters are `val`. For each parameter
  * written to in its body, renames it to `name$arg` and prepends a mutable local
  * `var name: T = name$arg`, so every body reference binds to the `var`. KNOWN LIMIT: a LAMBDA's
  * own reassigned parameter is not reached — degrades loudly as a compile error. */
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
    // StandardTraversal reaches every `DefDef`, including an anonymous class's methods (§3).
    locally {
      given Program = program
      val scan = new Phase:
        def name = "reassigned-params->var/scan"
        override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef = { scanDef(d); d }
      program.units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    }
    if argOf.isEmpty then return program

    // param symbol becomes a mutable local (same name/id -> references follow); fresh arg symbol
    // (isParam) takes the slot.
    val symbols0 = program.symbols.all.map { s =>
      if nowVar(s.id) then s.copy(flags = s.flags.copy(isParam = false, isMutable = true)) else s
    }
    // one decision row per METHOD whose parameter slots moved, not per parameter.
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
      // `copy`, never a fresh `Tree.Block`: preserves end-of-body trivia the rebuild would drop.
      case Some(b @ Tree.Block(stats, _, _, _, _)) =>
        // a constructor body must LEAD with `this(...)`/`super(...)` delegation.
        stats match
          case (deleg @ Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)) :: rest
              if isCtor && summon[Program].symbolOf(m).exists(_.name == "<init>") =>
            b.copy(stats = slotsInDelegation(deleg) :: (prelude ++ rest))
          case _ => b.copy(stats = prelude ++ stats)
      case Some(other) => Tree.Block(prelude, other, other.tpe, o)
      case None        => Tree.Block(prelude, Tree.Literal(Constant.UnitC, TypeRepr.NoType, o), TypeRepr.NoType, o)
    d.copy(paramss = paramss2, rhs = Some(body))

  /** A constructor's leading `super(…)`/`this(…)` reads the PARAMETER SLOTS, never the `var`s — the
    * `var` is prepended AFTER the delegation (JLS 8.8.7), so it does not exist yet there. Left
    * naming the repurposed parameter symbol, a promoted constructor's funnel hoists the delegation
    * into the `extends` clause, evaluated before the class body — `Not found: byteOffset`. */
  private def slotsInDelegation(deleg: Term)(using Program): Term =
    val toSlot = new Phase:
      def name = "reassigned-params->var/delegation"
      override def transformIdent(t: Tree.Ident)(using Program): Term =
        argOf.get(t.sym).map(a => t.copy(sym = a)).getOrElse(t)
    StandardTraversal.mapTerm(toSlot, deleg)

  /** Parameters (from `params`) that are the target of an assignment anywhere in `t`. Uses
    * [[StandardTraversal.scanTerm]] — total over `Term`, so no node kind can be missed (§3).
    * Descending into a `Tree.Lambda` body or a `Tree.New`'s anonymous-class body cannot false-positive:
    * javac refuses reassigning an enclosing method's parameter from either. */
  private def reassignedIn(t: Term, params: Set[SymId])(using Program): Set[SymId] =
    StandardTraversal.scanTerm(t, Set.empty[SymId]) { (found, x) =>
      x match
        case Tree.Assign(Tree.Ident(s, _, _), _, _, _, _) if params(s)   => found + s
        case Tree.IncDec(Tree.Ident(s, _, _), _, _, _, _) if params(s) => found + s // `p++`/`p--`
        case _                                                         => found
    }
