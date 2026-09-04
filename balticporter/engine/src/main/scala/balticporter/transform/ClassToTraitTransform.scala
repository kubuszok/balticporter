package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Rewrite a nominated abstract class into a trait and transform every subclass (named and
  * anonymous) to use `override val` members instead of constructor arguments. The nominated
  * type's `ClassDef` is rewritten in the TIR even when emission drops it (injected file), so
  * `CtorFunnel` sees a parent with no constructor to replay. CLAUDE.md §1(b). Empty specs = no-op. */
final class ClassToTraitTransform(
    val specs: Map[String, List[ClassToTraitTransform.ParamMapping]] = Map.empty,
) extends Phase, SurfacePolicy, MergeablePolicy:

  import ClassToTraitTransform.*

  def name: String = Name
  override def runsBefore: Set[String] = Set("package-rename")

  def surfaceFingerprint: String =
    specs.toList.sortBy(_._1).map { (fqn, mappings) =>
      val ms = mappings.sortBy(_.index).map(m => s"${m.index}:${m.valName}").mkString(",")
      s"$fqn[$ms]"
    }.mkString(";")

  def subjects: Set[String] = specs.keySet.map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ClassToTraitTransform =>
      val clashes = for
        (fqn, my) <- specs.toList; theirs <- o.specs.get(fqn).toList
        if my.sortBy(_.index) != theirs.sortBy(_.index)
      yield fqn
      if clashes.nonEmpty then Left(clashes.sorted.mkString(
        "both modules declare `class-to-trait` for ", ", ", " with DIFFERENT param mappings"))
      else Right(MergeablePolicy.Merged(
        new ClassToTraitTransform(specs ++ o.specs),
        (o.specs.keySet -- specs.keySet).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"`${later.name}` is not a `ClassToTraitTransform`")

  // ---- resolved at the start of run ----
  private var resolved = Map.empty[SymId, ResolvedSpec]
  private var fresh: () => SymId = () => SymId.None
  private var newSyms = List.newBuilder[Symbol]

  override def run(program: Program): Program =
    if specs.isEmpty then return program
    given Program = program

    resolved = (for
      (fqn, mappings) <- specs.toList
      sym             <- program.symbols.all.find(_.fullName == fqn)
    yield
      val parentCd = program.units.find(_.symbol == sym.id)
      val widestCtor = parentCd.toList.flatMap { cd =>
        cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
          .maxByOption(c => CtorFunnel.valueParams(program, c).size)
      }.headOption
      val formalTypes = widestCtor.toList.flatMap(c => CtorFunnel.valueParams(program, c).map(_.tpt.tpe))
      val defaults = parentCd.toList.flatMap { cd =>
        val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
        ctors.find(c => CtorFunnel.valueParams(program, c).isEmpty).toList.flatMap { nc =>
          CtorFunnel.stmtsOf(nc).headOption.collect { case t: Term => Tree.uncomment(t) }.toList.flatMap {
            case Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _)
                if program.symbolOf(m).exists(_.name == "<init>") => args
            case _ => Nil
          }
        }
      }
      val ownedFields = program.symbols.all.filter { s =>
        s.owner == sym.id && s.name != "<init>" && !s.flags.isStatic && !s.flags.isAbstract
      }.map(_.name).toSet
      // For each non-widest parent constructor, trace its `this(...)` delegation to the widest,
      // collecting the expanded argument list (arity via valueParams, matching superArgsOf).
      val widestArity = widestCtor.map(c => CtorFunnel.valueParams(program, c).size).getOrElse(0)
      val ctorDelegations: Map[Int, (List[SymId], List[Term])] = parentCd.toList.flatMap { cd =>
        val allCtors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
        allCtors.flatMap { ctor =>
          val arity = CtorFunnel.valueParams(program, ctor).size
          if arity >= widestArity then None // the widest itself needs no expansion
          else
            CtorFunnel.stmtsOf(ctor).headOption.collect { case t: Term => Tree.uncomment(t) }.flatMap {
              case Tree.Apply(Tree.Select(r, m, _, _), delegArgs, _, _, _)
                  if program.symbolOf(m).exists(_.name == "<init>") && !r.isInstanceOf[Tree.Super] && delegArgs.nonEmpty =>
                val paramSyms = CtorFunnel.valueParams(program, ctor).map(_.symbol)
                Some(arity -> (paramSyms, delegArgs))
              case _ => None
            }
        }
      }.toMap
      sym.id -> ResolvedSpec(fqn, mappings, formalTypes, defaults, ownedFields, sym.id, ctorDelegations)
    ).toMap

    if resolved.isEmpty then return program

    var nextId = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    fresh = () => { val id = SymId(nextId); nextId += 1; id }
    newSyms = List.newBuilder[Symbol]

    // 1) Transform the nominated types THEMSELVES into traits (declaration side)
    val units1 = program.units.map { cd =>
      if resolved.contains(cd.symbol) then transformNominatedType(cd, resolved(cd.symbol), program)
      else cd
    }

    // 2) Use the standard traversal to transform subclasses (named + anonymous)
    val p1 = program.rebuilt(units1, program.symbols)
    val units2 = p1.units.map(u => StandardTraversal.mapClassDef(this, u)(using p1))
    val symbols2 = StandardTraversal.mapSymbols(this, p1.symbols)(using p1)

    // 2b) Transitive subclass delegation: a class extending one REWRITTEN by step 2 calls
    // super(...) on it, so the same multi-root rewrite retargets narrower roots to this(...)
    // delegations on the widest constructor, promoting it as primary.
    val p2 = p1.rebuilt(units2, symbols2)
    val transitiveResolved: Map[SymId, ResolvedSpec] = buildTransitiveSpecs(p2)
    val units2b = if transitiveResolved.isEmpty then units2
    else
      val savedResolved = resolved
      resolved = transitiveResolved
      val result = p2.units.map(u => StandardTraversal.mapClassDef(this, u)(using p2))
      resolved = savedResolved
      result

    // 3) Update symbol flags for nominated types (isTrait) and their mapped fields
    val mappedValNames = resolved.values.flatMap(_.mappings.map(_.valName)).toSet
    val finalSyms = (if transitiveResolved.isEmpty then symbols2 else p2.symbols)
    val allSyms = finalSyms.all.map { s =>
      if resolved.contains(s.id) then
        s.copy(flags = s.flags.copy(isTrait = true, isAbstract = false))
      else if resolved.values.exists(_.parentSymId == s.owner) && s.name == "<init>" then
        // a trait has no constructor to call
        s.copy(flags = s.flags.copy(isAbstract = true))
      else if resolved.values.exists(_.parentSymId == s.owner) && mappedValNames.contains(s.name) &&
              !s.flags.isStatic then
        // override vals in subclasses
        s.copy(flags = s.flags.copy(isAbstract = true, isMutable = false))
      else s
    } ++ newSyms.result()

    p1.rebuilt(units2b, SymbolTable(allSyms))

  // ---- declaration side: transform the nominated type itself ----

  private def transformNominatedType(cd: Tree.ClassDef, spec: ResolvedSpec, program: Program): Tree.ClassDef =
    val origin = cd.origin
    val mappedFieldNames = spec.mappings.map(_.valName).toSet
    // Mapped fields are replaced by abstract vals; keeping both triggers the §4.55 shadow-rename
    // pass, renaming the field to `max$field` and breaking every `pool.max` reference.
    val bodyNoCtors = cd.body.filterNot {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case d: Tree.ValDef => program.symbolOf(d.symbol).exists(s => mappedFieldNames.contains(s.name))
      case _ => false
    }
    // DefDef, not ValDef: `resolveFieldShadowing`'s `implementsInherited` test matches a
    // parameterless, bodyless DefDef, treating the subclass's override val as an implementation
    // pair rather than a shadow (§4.55).
    val abstractVals = spec.mappings.flatMap { m =>
      if m.index < spec.formalTypes.size then
        val tpe = spec.formalTypes(m.index)
        val defId = fresh()
        newSyms += Symbol(defId, m.valName, s"${program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")}#${m.valName}",
          Flags(isAbstract = true, isProtected = true), cd.symbol, tpe, origin)
        Some(Tree.DefDef(defId, Nil, TypeTree(tpe, origin), None, origin))
      else None
    }
    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = program.symbolOf(cd.symbol).map(_.fullName).getOrElse(""),
      detail     = Map("why" -> "class-to-trait: abstract class -> trait with abstract vals"),
      reason     = Reason.Configured(name, spec.fqn),
      origin     = origin,
    ))
    cd.copy(body = abstractVals ++ bodyNoCtors)

  // ---- MiniPhase hooks: transform subclasses ----

  override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
    if resolved.isEmpty then return cd
    if resolved.contains(cd.symbol) then return cd // the nominated type itself, already handled
    parentSpec(cd) match
      case Some(spec) => rewriteNamedSubclass(cd, spec)
      case None       => cd



  // ---- named subclass rewrite ----

  private def rewriteNamedSubclass(cd: Tree.ClassDef, spec: ResolvedSpec)(using program: Program): Tree.ClassDef =
    val cdSym = program.symbolOf(cd.symbol) match
      case Some(s) => s
      case None    => return cd
    val origin = cd.origin
    val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }

    val ctorsWithSuperArgs = ctors
      .map(c => c -> CtorFunnel.superArgsOf(program, c))
      .filter(_._2.nonEmpty)

    val widestWithArgs = ctorsWithSuperArgs.maxByOption(_._2.size)

    // multi-root rewrite: when MULTIPLE constructors call super(...) on the nominated parent
    // (nilary super() included), rewrite the NARROWER ones into this(...) delegations to the
    // widest, making it the funnel's UNIQUE root so its params become override val RHSes.
    val roots = ctors.filterNot(c => CtorFunnel.delegatesToThis(program, c))
    val nilaryRoots = roots.filter { c =>
      CtorFunnel.valueParams(program, c).isEmpty &&
        CtorFunnel.headStmt(c).exists {
          case Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), _, _, _, _) =>
            program.symbolOf(m).exists(_.name == "<init>")
          case _ => false
        }
    }
    val superRootSyms = (ctorsWithSuperArgs.map(_._1.symbol) ++ nilaryRoots.map(_.symbol)).toSet
    val multipleRootsCallSuper = widestWithArgs.isDefined && superRootSyms.size > 1 &&
      (spec.ctorDelegations.nonEmpty || spec.defaults.nonEmpty)

    // Narrower roots are rewritten to this(...) delegations BEFORE isSafeArg is computed, so the
    // widest constructor becomes the unique root.
    val rewrittenBody: List[Statement] = if multipleRootsCallSuper then
      val widestCtor = widestWithArgs.get._1
      cd.body.map {
        case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") &&
            d.symbol != widestCtor.symbol && superRootSyms.contains(d.symbol) =>
          rewriteSuperToThis(d, spec, widestCtor.symbol)(using program)
        case other => other
      }
    else cd.body

    val ctors2 = rewrittenBody.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }

    val widestWithArgs2 = ctors2
      .map(c => c -> CtorFunnel.superArgsOf(program, c))
      .filter(_._2.nonEmpty)
      .maxByOption(_._2.size)

    // A super arg referencing a SECONDARY ctor's parameter becomes self-referential when placed
    // as an override val RHS — `override val max: Int = max` reads itself and evaluates to 0. After
    // the multi-root rewrite, the widest constructor is the unique root, so its params are safe.
    val ctorParamSyms: Set[SymId] = widestWithArgs2 match
      case Some((ctor, _)) => ctor.paramss.flatten.map(_.symbol).toSet
      case None => Set.empty

    def isSafeArg(arg: Term): Boolean = arg match
      case Tree.Ident(sym, _, _) =>
        if ctorParamSyms.contains(sym) then
          val roots2 = ctors2.filterNot(c => CtorFunnel.delegatesToThis(program, c))
          roots2.size == 1 && widestWithArgs2.exists(_._1.symbol == roots2.head.symbol)
        else true
      case _ => true

    val overrideVals = widestWithArgs2 match
      case Some((_, superArgs)) =>
        spec.mappings.flatMap { m =>
          if m.index < superArgs.size && isSafeArg(superArgs(m.index)) then
            val tpe = if m.index < spec.formalTypes.size then spec.formalTypes(m.index) else superArgs(m.index).tpe
            Some(mkVal(m, tpe, Some(superArgs(m.index)), origin, cd.symbol, cdSym))
          else if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
            Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, cd.symbol, cdSym))
          else None
        }
      case None =>
        if spec.defaults.size < spec.mappings.size then return cd
        spec.mappings.flatMap { m =>
          if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
            Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, cd.symbol, cdSym))
          else None
        }

    // A transitive subclass (empty mappings) needs only the multi-root delegation rewrite.
    if overrideVals.isEmpty && multipleRootsCallSuper then
      record(Decision(
        kind       = Decision.Kind.RetypedSignature,
        subject    = cd.symbol,
        subjectFqn = cdSym.fullName,
        detail     = Map("parent" -> spec.fqn, "why" -> "class-to-trait: multi-root delegation rewrite for transitive subclass"),
        reason     = Reason.Configured(name, spec.fqn),
        origin     = origin,
      ))
      return cd.copy(body = rewrittenBody)
    else if overrideVals.isEmpty then return cd

    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = cdSym.fullName,
      detail     = Map("parent" -> spec.fqn, "why" -> "class-to-trait: override vals replace super args"),
      reason     = Reason.Configured(name, spec.fqn),
      origin     = origin,
    ))

    // Super args are stripped from the widest constructor ONLY outside multi-root mode; in
    // multi-root mode they must stay so the CtorFunnel sees a unique root with super args and
    // promotes it as primary (the emitter skips super args when the parent is a trait).
    val strippedBody = widestWithArgs2 match
      case Some((widestCtor, _)) if !multipleRootsCallSuper =>
        rewrittenBody.map {
          case d: Tree.DefDef if d.symbol == widestCtor.symbol => stripSuperArgs(d)(using summon[Program])
          case other => other
        }
      case _ => rewrittenBody

    val strippedFields = spec.mappings.map(_.valName).toSet ++ spec.ownedFields
    val cleanedBody = strippedBody.map {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") =>
        stripReplayedAssignments(d, strippedFields)(using summon[Program])
      case other => other
    }

    val (ctorDefs, rest) = cleanedBody.partition {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case _ => false
    }
    cd.copy(body = ctorDefs ++ overrideVals ++ rest)



  override def transformApply(app: Tree.Apply)(using program: Program): Term =
    if resolved.isEmpty then return app
    // Extract the `New` from either `Apply(New(...), args)` or `Apply(TypeApply(New(...), targs), args)`.
    val newNode: Option[Tree.New] = app.fun match
      case n: Tree.New                        => Some(n)
      case Tree.TypeApply(n: Tree.New, _, _, _) => Some(n)
      case _                                  => None
    newNode match
      case None => return app
      case Some(n) =>
        val headSym = ClassToTraitTransform.headSymOf(n.tpt.tpe)
        headSym.flatMap(resolved.get) match
          case Some(spec) =>
            n.anon match
              case Some(anon) =>
                val origin = app.origin
                val anonSym = program.symbolOf(anon.symbol).getOrElse(return app)
                // WITH args: override vals from the actual args, falling back to defaults past
                // args.size (a partial constructor). WITHOUT args: all from defaults.
                val overrideVals = if app.args.nonEmpty then
                  spec.mappings.flatMap { m =>
                    if m.index < app.args.size then
                      val tpe = if m.index < spec.formalTypes.size then spec.formalTypes(m.index) else app.args(m.index).tpe
                      Some(mkVal(m, tpe, Some(app.args(m.index)), origin, anon.symbol, anonSym))
                    else if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
                      Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, anon.symbol, anonSym))
                    else None
                  }
                else if spec.defaults.size >= spec.mappings.size then
                  spec.mappings.flatMap { m =>
                    if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
                      Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, anon.symbol, anonSym))
                    else None
                  }
                else Nil
                val newAnon = if overrideVals.nonEmpty then anon.copy(body = overrideVals ++ anon.body) else anon
                val newNew = n.copy(anon = Some(newAnon))
                Tree.Apply(newNew, Nil, app.method, app.tpe, app.origin)
              case None =>
                // non-anonymous `new C(args)` — strip args
                if app.args.nonEmpty then Tree.Apply(n, Nil, app.method, app.tpe, app.origin)
                else app
          case _ => app


  /** Fix a RAW `new Pool[?](a,b) { ... }.asInstanceOf[Pool[T]]`: the `New` node's `tpt` carries
    * wildcards, making the anonymous class `Pool[Nothing]` and every abstract member unreachable,
    * while the `Typed` wrapper carries the real type argument. Replaces the wildcard args with the
    * `Typed` target's concrete ones, narrows `Object`-returning overrides, drops the wrapper. */
  override def transformTerm(t: Term)(using program: Program): Term =
    if resolved.isEmpty then return t
    t match
      case ty @ Tree.Typed(inner: Tree.Apply, castTpt, _, _) =>
        val n = inner.fun match
          case n: Tree.New if n.anon.isDefined => Some(n)
          case _                               => None
        n match
          case Some(newNode) =>
            val headSym = ClassToTraitTransform.headSymOf(newNode.tpt.tpe)
            headSym.flatMap(resolved.get) match
              case Some(_) =>
                // Match: tpt has wildcards (AppliedType with TypeBounds args) and target is concrete
                val hasWildcards = newNode.tpt.tpe match
                  case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
                  case _ => false
                val targetArgs = castTpt.tpe match
                  case TypeRepr.AppliedType(tc, args) if headSymOf(tc) == headSym &&
                       !args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) => Some(args)
                  case _ => None
                (hasWildcards, targetArgs) match
                  case (true, Some(concreteArgs)) =>
                    val baseTc = newNode.tpt.tpe match
                      case TypeRepr.AppliedType(tc, _) => tc
                      case other => other
                    val fixedTpt = newNode.tpt.copy(tpe = TypeRepr.AppliedType(baseTc, concreteArgs))
                    val fixedAnon = newNode.anon.map { anon =>
                      if concreteArgs.size == 1 then
                        val actualTpe = concreteArgs.head
                        val fixedBody = anon.body.map {
                          case d: Tree.DefDef if !program.symbolOf(d.symbol).exists(_.name == "<init>") =>
                            val isObject = d.returnTpt.tpe match
                              case TypeRepr.TypeRef(_, s) => program.symbolOf(s).exists(n =>
                                n.fullName == "java.lang.Object" || n.name == "Object")
                              case _ => false
                            if isObject then
                              d.copy(returnTpt = d.returnTpt.copy(tpe = actualTpe))
                            else d
                          case other => other
                        }
                        anon.copy(body = fixedBody)
                      else anon
                    }
                    val fixedNew = newNode.copy(tpt = fixedTpt, anon = fixedAnon)
                    // drop the Typed wrapper -- the anonymous class now directly extends the parent
                    inner.copy(fun = fixedNew, tpe = castTpt.tpe)
                  case _ => t
              case _ => t
          case _ => t
      case _ => t

  // ---- multi-root rewrite: super(args) -> this(expanded_args) for narrower roots ----

  /** Rewrite a narrower root's `super(args)` into `this(expanded_args)` targeting the widest
    * constructor of THIS class (`targetCtor`), using the parent's own delegation chain. */
  private def rewriteSuperToThis(d: Tree.DefDef, spec: ResolvedSpec, targetCtor: SymId)(using program: Program): Tree.DefDef =
    val superArgs = CtorFunnel.superArgsOf(program, d)
    val arity = superArgs.size
    // Nilary super(): expand using defaults.
    if arity == 0 then
      if spec.defaults.nonEmpty then rewriteSuperCallToThis(d, spec.defaults, targetCtor)
      else d
    else
      spec.ctorDelegations.get(arity) match
        case None => d // No delegation chain found for this arity
        case Some((parentParamSyms, delegArgs)) =>
          val subst = parentParamSyms.zip(superArgs).toMap
          val expandedArgs = delegArgs.map(substituteSymbols(_, subst))
          rewriteSuperCallToThis(d, expandedArgs, targetCtor)

  /** Replace the head `super(args)` with `this(newArgs)` targeting `targetCtor` of THIS class. */
  private def rewriteSuperCallToThis(d: Tree.DefDef, newArgs: List[Term], targetCtor: SymId)(using program: Program): Tree.DefDef =
    val stmts = CtorFunnel.stmtsOf(d)
    stmts.headOption.collect { case t: Term => t } match
      case None => d
      case Some(head) =>
        val unwrapped = Tree.uncomment(head)
        unwrapped match
          case Tree.Apply(Tree.Select(sup: Tree.Super, m, sel, selOrigin), _, method, tpe, appOrigin)
              if program.symbolOf(m).exists(_.name == "<init>") =>
            // Replace Super with a self-reference (This), targeting the WIDEST constructor of
            // THIS class (not the parent's) — the one the funnel will promote to primary.
            val thisNode = Tree.This(sup.cls, sup.tpe, sup.origin)
            val newSel = Tree.Select(thisNode, targetCtor, sel, selOrigin)
            val newApply: Term = Tree.Apply(newSel, newArgs, method, tpe, appOrigin)
            val newHead: Term = Tree.recomment(head, newApply)
            val newStmts = (newHead: Statement) :: stmts.tail
            val trailing = CtorFunnel.trailingOf(d)
            val newBody: Term = newStmts match
              case List(single: Term) if trailing.isEmpty => single
              case _ =>
                val last = newStmts.last match { case t: Term => t; case _ => return d }
                Tree.Block(newStmts.init.collect { case s: Statement => s }, last,
                           d.rhs.map(_.tpe).getOrElse(TypeRepr.NoType), d.origin, trailing)
            d.copy(rhs = Some(newBody))
          case _ => d

  /** Substitute symbol references in a term: replace Ident(sym) with the mapped term. */
  private def substituteSymbols(t: Term, subst: Map[SymId, Term]): Term = t match
    case Tree.Ident(sym, tpe, origin) if subst.contains(sym) => subst(sym)
    case Tree.Apply(fun, args, method, tpe, origin) =>
      val newFun = fun match { case f: Term => substituteSymbols(f, subst); case other => other }
      Tree.Apply(newFun, args.map(substituteSymbols(_, subst)), method, tpe, origin)
    case Tree.Select(qual, sym, name, origin) =>
      val newQual = qual match { case q: Term => substituteSymbols(q, subst); case other => other }
      Tree.Select(newQual, sym, name, origin)
    case other => other

  // ---- transitive subclass rewrite ----

  /** ResolvedSpecs for classes extending one REWRITTEN by the main pass (step 2): no mappings (no
    * override vals), only the delegation chain data the multi-root rewrite needs. */
  private def buildTransitiveSpecs(p: Program): Map[SymId, ResolvedSpec] =
    val rewrittenSyms = resolved.values.map(_.parentSymId).toSet
    val specs = collection.mutable.Map[SymId, ResolvedSpec]()
    p.units.flatMap(u => StandardTraversal.allClassDefs(u)(using p)).foreach { cd =>
      val parentSym = cd.parents.iterator.flatMap {
        case tt: TypeTree => ClassToTraitTransform.headSymOf(tt.tpe)
        case t: Term => ClassToTraitTransform.headSymOf(t.tpe)
      }.nextOption()
      parentSym match
        case Some(ps) if !rewrittenSyms.contains(ps) && !resolved.contains(ps) =>
          // Check if this parent was rewritten: does it have a parent that IS a nominated type?
          val parentCd = p.units.flatMap(u => StandardTraversal.allClassDefs(u)(using p))
            .find(_.symbol == ps)
          val parentIsRewritten = parentCd.exists { pcd =>
            pcd.parents.iterator.flatMap {
              case tt: TypeTree => ClassToTraitTransform.headSymOf(tt.tpe)
              case t: Term => ClassToTraitTransform.headSymOf(t.tpe)
            }.exists(resolved.contains)
          }
          if parentIsRewritten then
            parentCd.foreach { pcd =>
              val ctors = pcd.body.collect { case d: Tree.DefDef if p.symbolOf(d.symbol).exists(_.name == "<init>") => d }
              // Use valueParams (excluding using clauses) for arity, matching superArgsOf's count
              val widestCtor = ctors.maxByOption(c => CtorFunnel.valueParams(p, c).size)
              val widestArity = widestCtor.map(c => CtorFunnel.valueParams(p, c).size).getOrElse(0)
              if widestArity > 0 then
                val formalTypes = widestCtor.toList.flatMap(c => CtorFunnel.valueParams(p, c).map(_.tpt.tpe))
                // after rewrite, the parent's constructors carry this(...) delegations to the widest.
                val defaults = ctors.find(c => CtorFunnel.valueParams(p, c).isEmpty).toList.flatMap { nc =>
                  CtorFunnel.stmtsOf(nc).headOption.collect { case t: Term => Tree.uncomment(t) }.toList.flatMap {
                    case Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _)
                        if p.symbolOf(m).exists(_.name == "<init>") => args
                    case _ => Nil
                  }
                }
                val ctorDelegations: Map[Int, (List[SymId], List[Term])] = ctors.flatMap { ctor =>
                  val arity = CtorFunnel.valueParams(p, ctor).size
                  if arity >= widestArity then None
                  else
                    CtorFunnel.stmtsOf(ctor).headOption.collect { case t: Term => Tree.uncomment(t) }.flatMap {
                      case Tree.Apply(Tree.Select(r, m, _, _), delegArgs, _, _, _)
                          if p.symbolOf(m).exists(_.name == "<init>") && !r.isInstanceOf[Tree.Super] && delegArgs.nonEmpty =>
                        Some(arity -> (CtorFunnel.valueParams(p, ctor).map(_.symbol), delegArgs))
                      case _ => None
                    }
                }.toMap
                if ctorDelegations.nonEmpty || defaults.nonEmpty then
                  specs(ps) = ResolvedSpec(
                    fqn = p.symbolOf(ps).map(_.fullName).getOrElse(""),
                    mappings = Nil, // No override vals for transitive subclasses
                    formalTypes = formalTypes,
                    defaults = defaults,
                    ownedFields = Set.empty,
                    parentSymId = ps,
                    ctorDelegations = ctorDelegations,
                  )
            }
        case _ => ()
    }
    specs.toMap

  // ---- helpers ----

  private def mkVal(m: ParamMapping, tpe: TypeRepr, rhs: Option[Term],
      origin: Origin, owner: SymId, ownerSym: Symbol): Tree.ValDef =
    val valId = fresh()
    newSyms += Symbol(valId, m.valName, s"${ownerSym.fullName}#${m.valName}",
      Flags(isOverride = true, isProtected = true), owner, tpe, origin)
    Tree.ValDef(valId, TypeTree(tpe, origin), rhs, origin)

  private def parentSpec(cd: Tree.ClassDef)(using Program): Option[ResolvedSpec] =
    cd.parents.iterator.flatMap {
      case tt: TypeTree => headSymOf(tt.tpe)
      case t: Term      => headSymOf(t.tpe)
    }.collectFirst { case s if resolved.contains(s) => resolved(s) }

  private def stripSuperArgs(d: Tree.DefDef)(using program: Program): Tree.DefDef =
    val stmts = CtorFunnel.stmtsOf(d)
    CtorFunnel.headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
          if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
        val newHead: Term = stmts.head match
          case t: Term => stripTopSuperArgs(Tree.uncomment(t))(using program) match
              case Some(stripped) => Tree.recomment(t, stripped)
              case None           => t
          case _ => return d
        val newStmts = newHead :: stmts.tail
        val trailing = CtorFunnel.trailingOf(d)
        val newBody: Term = newStmts match
          case List(single: Term) if trailing.isEmpty => single
          case _ =>
            val last = newStmts.last match { case t: Term => t; case _ => return d }
            Tree.Block(newStmts.init.collect { case s: Statement => s }, last,
                       d.rhs.map(_.tpe).getOrElse(TypeRepr.NoType), d.origin, trailing)
        d.copy(rhs = Some(newBody))
      case _ => d

  private def stripTopSuperArgs(t: Term)(using program: Program): Option[Term] = t match
    case Tree.Apply(sel @ Tree.Select(_: Tree.Super, m, _, _), args, method, tpe, origin)
        if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
      Some(Tree.Apply(sel, Nil, method, tpe, origin))
    case _ => None

  private def stripReplayedAssignments(d: Tree.DefDef, fieldNames: Set[String])(using program: Program): Tree.DefDef =
    d.rhs match
      case Some(body) =>
        val newBody = stripAssigns(body, fieldNames)
        if newBody ne body then d.copy(rhs = Some(newBody)) else d
      case None => d

  private def stripAssigns(t: Term, fieldNames: Set[String])(using program: Program): Term = t match
    case Tree.Block(stmts, expr, tpe, origin, trailing) =>
      val filtered = stmts.flatMap {
        case a: Tree.Assign if isFieldAssign(a, fieldNames) => None
        case s: Term   => Some(stripAssigns(s, fieldNames))
        case other     => Some(other)
      }
      val newExpr = stripAssigns(expr, fieldNames)
      if (filtered ne stmts) || (newExpr ne expr) then Tree.Block(filtered, newExpr, tpe, origin, trailing) else t
    case _ => t

  private def isFieldAssign(a: Tree.Assign, fieldNames: Set[String])(using program: Program): Boolean =
    a.lhs match
      case Tree.Select(_, sym, _, _) => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case Tree.Ident(sym, _, _)     => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case _                         => false

object ClassToTraitTransform:
  val Name: String = "class-to-trait"

  final case class ParamMapping(index: Int, valName: String,
    defaultLiteral: Option[Term] = None)

  private[transform] final case class ResolvedSpec(
    fqn: String, mappings: List[ParamMapping], formalTypes: List[TypeRepr],
    defaults: List[Term], ownedFields: Set[String], parentSymId: SymId,
    /** For each non-widest parent constructor: its arity -> the expanded argument list to the
      * widest constructor, with the parent's own parameter SYMBOLS still in place — a subclass
      * calling `super(args)` at that arity substitutes its actual args for those symbols to build
      * the `this(expanded)` delegation. Built from the parent's own `this(...)` delegation chain. */
    ctorDelegations: Map[Int, (List[SymId], List[Term])] = Map.empty,
  )

  private[transform] def headSymOf(tpe: TypeRepr): Option[SymId] = tpe match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None
