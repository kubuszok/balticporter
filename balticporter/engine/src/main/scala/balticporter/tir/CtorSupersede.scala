package balticporter.tir

/** Which PROMOTED constructor parameters are emitted AS the same-named field they fill (`class C(var x: T)`): a promotion makes the parameter a member, so beside its field scala needs either a rename
  * (a parameter name is surface — `new C(x = 1)`) or one member. One derivation, read by the rename pass, the emitter and its notes.
  *
  * The merged field holds the argument from allocation on, where java's held its default until `this.x = x`; each guard below rules out a READ in that window, and a refused pair keeps the rename.
  */
object CtorSupersede:

  final case class Result(merged: Map[SymId, SymId], refused: Map[SymId, String])

  object Guard:
    val TypeDiffers        = "type-differs"
    val NoSelfAssignment   = "no-single-self-assignment"
    val ParamReassigned    = "parameter-reassigned"
    val FieldInitialiser   = "field-initialiser"
    val ReadBeforeAssign   = "read-before-assignment"
    val SuperDispatch      = "super-constructor-dispatch"
    val ExternalSuper      = "external-superclass"
    val ReplayedField      = "field-written-by-a-replay"
    val ExternalAnnotation = "class-file-annotation"

  def of(program: Program, cd: Tree.ClassDef, plan: CtorFunnel.Plan, replayed: Set[SymId] = Set.empty): Result =
    given Program = program
    val cls       = program.symbolOf(cd.symbol)
    if plan.primary.isEmpty || plan.isSynthesised || cls.exists(s => s.flags.isEnum || s.flags.isRecord || s.flags.isTrait) then Result(Map.empty, Map.empty)
    else
      def nameOf(id: SymId): String = program.symbolOf(id).map(_.name).getOrElse("")
      val fields = cd.body.collect { case v: Tree.ValDef if !program.symbolOf(v.symbol).exists(_.flags.isStatic) => v }
      val byName = fields.map(f => nameOf(f.symbol) -> f).toMap
      val pairs = plan.primaryParams.flatMap(p => byName.get(nameOf(p.symbol)).map(p -> _))
      if pairs.isEmpty then Result(Map.empty, Map.empty)
      else
        val body = plan.primaryBody
        def selfAssign(s: Statement, f: SymId, p: SymId): Boolean = elided(s, Map(p -> f))
        val superOk = superChainSilent(program, cd)
        val merged = Map.newBuilder[SymId, SymId]
        val refused = Map.newBuilder[SymId, String]
        pairs.foreach { (p, f) =>
          val at    = body.indexWhere(selfAssign(_, f.symbol, p.symbol))
          val guard =
            if p.tpt.tpe != f.tpt.tpe then Some(Guard.TypeDiffers)
            else if at < 0 || body.count(selfAssign(_, f.symbol, p.symbol)) != 1 then Some(Guard.NoSelfAssignment)
            else if body.exists(s => writes(s, p.symbol)) then Some(Guard.ParamReassigned)
            else if f.rhs.exists(r => !r.isInstanceOf[Tree.Literal]) then Some(Guard.FieldInitialiser)
            else if program.symbolOf(f.symbol).exists(_.annotations.exists(a => !ownedType(program, a.tpe))) then Some(Guard.ExternalAnnotation)
            // a subclass replaying this constructor's body writes the field again: a `var` takes it, a `val` cannot
            else if replayed(f.symbol) && !program.symbolOf(f.symbol).exists(_.flags.isMutable) then Some(Guard.ReplayedField)
            else
              // java runs every field initialiser and initialiser block, then the statements before the assignment
              val window = fields.flatMap(_.rhs) ++ initBlocks(program, cd) ++ plan.superArgs ++ body.take(at).flatMap(termsOf)
              if window.exists(t => readsThis(program, t, Some(f.symbol))) then Some(Guard.ReadBeforeAssign)
              else superOk
          guard match
            case Some(g) => refused += p.symbol -> g
            case None    => merged += p.symbol -> f.symbol
        }
        Result(merged.result(), refused.result())

  /** the `this.x = x` a merged pair no longer needs: the promotion performs it. */
  def elided(s: Statement, merged: Map[SymId, SymId]): Boolean = s match
    case Tree.Assign(lhs, Tree.Ident(r, _, _), _, _, scala.None) =>
      merged.get(r).exists { f =>
        lhs match
          case Tree.Select(_: Tree.This, l, _, _) => l == f
          case Tree.Ident(l, _, _)                => l == f
          case _                                  => false
      }
    case _ => false

  private def ownedType(program: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s)      => program.definitionOf(s).isDefined
    case TypeRepr.AppliedType(tc, _) => ownedType(program, tc)
    case _                           => false

  private def termsOf(s: Statement): List[Term] = s match
    case t: Term        => List(t)
    case v: Tree.ValDef => v.rhs.toList
    case _ => Nil

  private def initBlocks(program: Program, cd: Tree.ClassDef): List[Term] =
    cd.body.collect {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(s => s.name == "<initblock>" && !s.flags.isStatic) => d.rhs.toList
    }.flatten

  private def writes(s: Statement, p: SymId)(using Program): Boolean =
    termsOf(s).exists(t =>
      StandardTraversal.scanTerm(t, false) { (acc, x) =>
        acc || (x match
          case Tree.Assign(Tree.Ident(l, _, _), _, _, _, _) => l == p
          case _                                            => false)
      }
    )

  /** Can evaluating `t` observe the object under construction: a `this` other than a field's qualifier, a `super`, an instance METHOD, or (`f` given) a read of the merged field itself.
    */
  private def readsThis(program: Program, t: Term, f: Option[SymId]): Boolean =
    given Program                = program
    def isField(s: SymId)        = program.definitionOf(s).exists(_.isInstanceOf[Tree.ValDef])
    def instanceMember(s: SymId) =
      program.symbolOf(s).exists(sy => !sy.flags.isStatic && program.definitionOf(sy.owner).exists(_.isInstanceOf[Tree.ClassDef]))
    val qualifiers = collection.mutable.ArrayBuffer.empty[Tree.This]
    val thises     = collection.mutable.ArrayBuffer.empty[Tree.This]
    val bad        = StandardTraversal.scanTerm(t, false) { (acc, x) =>
      x match
        case Tree.Select(q: Tree.This, s, _, _) if isField(s) => qualifiers += q; acc || f.contains(s)
        case th: Tree.This  => thises += th; acc
        case _:  Tree.Super => true
        case Tree.Ident(s, _, _) => acc || f.contains(s) || (instanceMember(s) && !isField(s))
        case _                   => acc
    }
    bad || thises.exists(th => !qualifiers.exists(_ eq th))

  /** The superclass constructors run before the merged field's java assignment and after scala's: they must not dispatch back into the object. Every owned ancestor's constructors, initialisers and
    * field initialisers are read; an unowned ancestor other than `java.lang.Object` is not readable and refuses.
    */
  private def superChainSilent(program: Program, cd: Tree.ClassDef): Option[String] =
    def classParent(c: Tree.ClassDef): Option[SymId] =
      c.parents.iterator.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }.flatMap(head).find(s => !program.symbolOf(s).exists(_.flags.isTrait))
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => scala.None
    @annotation.tailrec
    def walk(c: Tree.ClassDef, seen: Set[SymId]): Option[String] =
      classParent(c) match
        case scala.None                                                              => scala.None
        case Some(s) if program.symbolOf(s).exists(_.fullName == "java.lang.Object") => scala.None
        case Some(s) if seen(s)                                                      => scala.None
        case Some(s)                                                                 =>
          program.definitionOf(s) match
            case Some(pc: Tree.ClassDef) =>
              val fieldInits = pc.body.collect { case v: Tree.ValDef if !program.symbolOf(v.symbol).exists(_.flags.isStatic) => v.rhs.toList }.flatten
              val ctorStmts  = CtorFunnel.ctorsOf(program, pc.body).flatMap(d => CtorFunnel.bodyAfterDelegation(program, d)).flatMap(termsOf)
              val terms      = fieldInits ++ initBlocks(program, pc) ++ ctorStmts
              if terms.exists(t => readsThis(program, t, scala.None)) then Some(Guard.SuperDispatch)
              else walk(pc, seen + s)
            case _ => Some(Guard.ExternalSuper)
    walk(cd, Set(cd.symbol))
