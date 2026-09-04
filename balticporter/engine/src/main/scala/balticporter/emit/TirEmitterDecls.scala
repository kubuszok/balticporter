package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Class/enum declaration and constructor/class-initialiser ordering emission split out of TirEmitter (context diet S1). */
private[emit] trait TirEmitterDecls:
  self: TirEmitter =>

  // ---- definitions ----
  /** the classes currently being rendered, outermost first. Lets a `Tree.This` naming an ENCLOSING
    * class render Java's qualified `Outer.this` rather than a bare `this` (which names the inner one). */
  private[emit] val classStack = collection.mutable.ArrayDeque[SymId]()

  private[emit] def classDef(cd: Tree.ClassDef, i: Int): String =
    val outer = currentClass
    currentClass = Some(cd)
    classStack.append(cd.symbol)
    try classDef0(cd, i) finally { classStack.removeLast(); currentClass = outer }

  /** Parameter symbol to type for raw-parent override alignment. Uses `deWildcardedArgs`
    * substitution so parent and override agree by construction. */
  private[emit] def rawParentAlignment: Map[SymId, TypeRepr] =
    val out    = collection.mutable.Map[SymId, TypeRepr]()
    val done   = collection.mutable.Set[SymId]()
    val declOf = collection.mutable.Map[SymId, Tree.ClassDef]()
    allDeclaredClasses.foreach(cd => declOf(cd.symbol) = cd)
    def methodsOf(cd: Tree.ClassDef) = cd.body.collect {
      case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d
    }
    // Lazy: only built when a wildcard-carrying override needs it.
    lazy val graph = OverrideGraph.build(program)
    val chainOf = collection.mutable.Map[SymId, Set[SymId]]()
    /** Override of `od` reached through parent `pcd`, via `OverrideGraph` (filtered to that edge's chain). */
    def inheritedThrough(od: Tree.DefDef, pcd: Tree.ClassDef): Option[Tree.DefDef] =
      val chain = chainOf.getOrElseUpdate(pcd.symbol,
        (pcd.symbol :: graph.ancestorsOf(pcd.symbol)).toSet)
      graph.overridden(od.symbol).iterator
        .filter(m => chain(graph.ownerOf(m)))
        .flatMap(m => program.definitionOf(m).collect { case d: Tree.DefDef => d })
        .nextOption()
    /** parents first, so a grandchild aligns against its parent's ALREADY-aligned view. */
    def visit(cd: Tree.ClassDef, seen: Set[SymId]): Unit =
      if !done(cd.symbol) && !seen(cd.symbol) then
        done += cd.symbol
        val ours = methodsOf(cd)
        for
          p    <- cd.parents
          pt    = p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
          tycon <- headSymOf(pt)
          pcd  <- declOf.get(tycon)
        do
          visit(pcd, seen + cd.symbol)
          val subst = pt match
            case TypeRepr.AppliedType(tc, args)
                if args.exists { case _: TypeRepr.TypeBounds => true; case _ => false } &&
                   pcd.tparams.sizeIs == args.size =>
              pcd.tparams.map(_.symbol).zip(deWildcardedArgs(tc, args))
                .collect { case (s, Some(t)) => s -> t }.toMap
            case _ => Map.empty[SymId, TypeRepr]
          for
            od <- ours
            pd <- inheritedThrough(od, pcd)
            (ops, pps) <- od.paramss.zip(pd.paramss)
            (op, pp)   <- ops.zip(pps)
            if hasWildcardArg(op.tpt.tpe) && !out.contains(op.symbol)
          do
            val aligned = substTp(out.getOrElse(pp.symbol, pp.tpt.tpe), subst)
            // Require head constructor agreement (guard for approximate descriptor-missing edges).
            if !hasWildcardArg(aligned) && headSymOf(aligned) == headSymOf(op.tpt.tpe) then
              out(op.symbol) = aligned
              // JS-G06 citation (not obligation -- whole-program pass).
              catalog.cite(JS.G(6), sym(od.symbol).fullName)
    program.units.foreach(u => declOf.values.foreach(visit(_, Set.empty)))
    out.toMap

  private[emit] lazy val overrideAlign: Map[SymId, TypeRepr] = rawParentAlignment

  /** Override alignment against injected parents' wildcard bounds. // ENGINE-LIMITS K35 (closed) */
  private[emit] lazy val injectedOverrideTypes: Map[SymId, TypeRepr] =
    if injectedSurface.isEmpty then Map.empty
    else
      val out = collection.mutable.Map[SymId, TypeRepr]()
      for
        cd  <- allDeclaredClasses
        p   <- cd.parents
        pt   = p match { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
        pSym <- headSymOf(pt).flatMap(program.symbolOf)
        if Substituted.tags(pSym)
      do
        // Build a type-parameter substitution map from the parent's type params to the child's
        // actual type arguments. e.g. for `extends Pool[T]` where Pool has type param `A`:
        // Map("A" -> TypeRepr representing T)
        val parentTParams = injectedSurface.typeParams.getOrElse(pSym.fullName, Nil)
        val actualArgs: List[TypeRepr] = pt match
          case TypeRepr.AppliedType(_, args) => args
          case _ => Nil
        val tparamSubst: Map[String, TypeRepr] =
          if parentTParams.size == actualArgs.size then parentTParams.zip(actualArgs).toMap
          else Map.empty

        for
          od  <- cd.body.collect { case d: Tree.DefDef if sym(d.symbol).name != "<init>" => d }
          injSig <- injectedSurface.lookup(pSym.fullName, sym(od.symbol).name,
                      od.paramss.flatten.size)
          injParams = injSig.paramTypes.flatten
          (ops, ip) <- od.paramss.flatten.zip(injParams)
          if !overrideAlign.contains(ops.symbol) // do not override rawParentAlignment
        do
          // Detect if the injected type has a wildcard bound that the TIR type does not.
          // Parse the injected type string to detect `? <: X` patterns and build a TypeRepr.
          val tirType = ops.tpt.tpe
          val injRendered = ip.rendered
          val aligned = alignToInjected(tirType, injRendered, tparamSubst)
          if aligned != tirType then out(ops.symbol) = aligned
      out.toMap

  /** Align a TIR type to an injected parent's wildcard bounds. */
  private[emit] def alignToInjected(tirType: TypeRepr, injected: String,
                               tparamSubst: Map[String, TypeRepr]): TypeRepr =
    // Quick check for wildcard in injected type.
    if !injected.contains("?") then tirType
    else tirType match
      case TypeRepr.AppliedType(tc, tirArgs) =>
        // Parse injected type's arguments for wildcard bounds.
        val injArgStr = extractTypeArgs(injected)
        if injArgStr.size != tirArgs.size then tirType
        else
          val newArgs = tirArgs.zip(injArgStr).map { (tirArg, injArg) =>
            val trimmed = injArg.trim
            if trimmed.startsWith("?") then
              // Parse bound: `? <: X`, `? >: X`, or bare `?`.
              val upperBound = """^\?\s*<:\s*(.+)$""".r
              val lowerBound = """^\?\s*>:\s*(.+)$""".r
              trimmed match
                case upperBound(boundName) =>
                  val resolvedBound = tparamSubst.getOrElse(boundName.trim, tirArg)
                  TypeRepr.TypeBounds(TypeRepr.NoType, resolvedBound)
                case lowerBound(boundName) =>
                  val resolvedBound = tparamSubst.getOrElse(boundName.trim, tirArg)
                  TypeRepr.TypeBounds(resolvedBound, TypeRepr.NoType)
                case _ =>
                  TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType)
            else tirArg
          }
          if newArgs == tirArgs then tirType
          else TypeRepr.AppliedType(tc, newArgs)
      case _ => tirType

  /** Extract type argument strings from a rendered type like `Foo[A, B, ? <: C]`. */
  private[emit] def extractTypeArgs(rendered: String): List[String] =
    val i = rendered.indexOf('[')
    if i < 0 then Nil
    else
      val inner = rendered.substring(i + 1, rendered.lastIndexOf(']'))
      // Split on commas at depth 0 (not inside nested brackets)
      val args = List.newBuilder[String]
      var depth = 0
      val sb = new StringBuilder
      for c <- inner do
        if c == '[' then { depth += 1; sb.append(c) }
        else if c == ']' then { depth -= 1; sb.append(c) }
        else if c == ',' && depth == 0 then { args += sb.toString; sb.clear() }
        else sb.append(c)
      if sb.nonEmpty then args += sb.toString
      args.result()

  /** An ARGUMENT reaching a parameter [[rawParentAlignment]] re-rendered. Java accepted the call
    * because the callee's formal was RAW there (`ParticleEffect.save(AssetManager, ResourceData)`
    * taking a `ResourceData<ParticleEffect>`); once the formal reads `ResourceData[Object]` the
    * conversion java made silently has to be written. Only fires where the argument disagrees. */
  private[emit] def alignedArgs(m: SymId, args: List[Term], i: Int): Option[List[String]] =
    val ps = program.definitionOf(m).collect { case d: Tree.DefDef => d.paramss.flatten }.getOrElse(Nil)
    if ps.sizeIs != args.size || !ps.exists(v => overrideAlign.contains(v.symbol)) then scala.None
    else Some(args.zip(ps).map { (a, v) =>
      overrideAlign.get(v.symbol).filter(_ != a.tpe) match
        case Some(t) => s"${operand(a, i)}.asInstanceOf[${tpe(t)}]"
        case None    => term(a, i)
    })

  /** A cast onto a parameter that [[rawParentAlignment]] re-rendered must land on the type the
    * parameter now HAS. The frontend built these casts against the raw fill's `ResourceData[?]`;
    * once the declaration reads `ResourceData[Object]` the same cast narrows to a wildcard the
    * callee will not take — and a cast to `T[?]` asserts nothing in the first place, so following
    * the alignment loses nothing. Only wildcarded targets on an aligned symbol are touched. */
  /** a POLY EXPRESSION (JLS 15.2) — a lambda or a method reference, the two java forms that have no
    * type of their own in EITHER language and take one from the slot they fill. The emitter's own
    * copy of `SpoonTir.polyExpression`'s question, asked of the TIR rather than of Spoon, because
    * this is the one place a cast on such a term can still be reached: the frontend's rule stops
    * the ENGINE writing one, and a cast the java SOURCE wrote is kept by design. `uncomment`,
    * because trivia wraps a term without changing what it is. */
  private[emit] def polyOperand(t: Term): Boolean = Tree.uncomment(t) match
    case _: Tree.Lambda | _: Tree.MethodRef => true
    // …and a CONDITIONAL over them, which JLS 15.25 makes a poly expression in its own right: java
    // pushes the target type through the `?:` and types each branch against it. Rendered as a CAST
    // this is the failure `polyExpression`'s refusal is about, one node out — the branches
    // elaborate to `Function1`s first and the cast then asserts that a `Function1` is the
    // functional interface, which throws. As an ASCRIPTION scala propagates the expected type into
    // both arms exactly as java did.
    case Tree.If(_, th, el, _, _)           => polyOperand(th) && polyOperand(el)
    case _                                  => false

  private[emit] def castTarget(e: Term, target: TypeRepr): TypeRepr =
    if !hasWildcardArg(target) then target
    else
      val s = e match
        case Tree.Ident(x, _, _)     => Some(x)
        case Tree.Select(_, x, _, _) => Some(x)
        case _                       => scala.None
      s.flatMap(overrideAlign.get).getOrElse(target)

  /** `this` in Scala always names the INNERMOST class, where Java's `Outer.this` names an enclosing
    * one. Qualify by simple name when the symbol is an enclosing class actually being rendered
    * around this point; anything else (an inherited/unknown owner) keeps the bare `this`.
    *
    * A SUPERTYPE is never qualified even when it also encloses: libGDX nests subclasses inside their
    * own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`), and constructor replay
    * moves the base's `this` statements into the subclass body — there the bare `this` is exactly
    * right, while `DynamicsModifier.this` would name the companion object. */
  private[emit] def thisRef(s: SymId): String =
    val inner = classStack.lastOption
    if inner.contains(s) || !classStack.contains(s) || inner.exists(inheritsFrom(_, s)) then "this"
    else s"${esc(sym(s).name)}.this"

  /** is `child` `anc`, or a (transitive) subtype of it, among our own definitions? */
  private[emit] def inheritsFrom(child: SymId, anc: SymId): Boolean =
    val seen = collection.mutable.Set[SymId]()
    def go(c: SymId): Boolean =
      c == anc || (seen.add(c) && program.definitionOf(c).collect { case cd: Tree.ClassDef =>
        parentSymsOf(cd).exists(go)
      }.getOrElse(false))
    go(child)

  private[emit] def classDef0(cd: Tree.ClassDef, i: Int): String =
    // The owner is set for BOTH lowerings. An enum's own members need a `private` qualifier by the
    // same rule an ordinary nested class's do, and dispatching before the assignment gave a nested
    // enum the ENCLOSING type's context — a bare `private` where java's scope is the whole
    // top-level enclosure.
    val savedOwner = currentOwnerSym
    currentOwnerSym = cd.symbol
    try (if sym(cd.symbol).flags.isEnum then enumDef(cd, i) else classDef1(cd, i))
    finally currentOwnerSym = savedOwner

  private[emit] def classDef1(cd: Tree.ClassDef, i: Int): String =
    val s  = sym(cd.symbol)
    // A NESTED type carries its own notes at its `class` keyword; the TOP-LEVEL one's are the
    // file's (`unitNotes`) and must not be printed twice.
    val cnote = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote = bodyNotes(cd.symbol, i + 1)
    val kw =
      if s.flags.isModule then "object"
      else if s.flags.isTrait then "trait"
      else "class"
    val tps     = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
    // lower Java constructors: `CtorFunnel` nominates one to become Scala's PRIMARY. Its body is
    // inlined (those statements run at construction), its `super(args)` moves into the `extends`
    // clause (which also fixes parents that need constructor arguments), and its PARAMETERS become
    // the class's parameters. Every other constructor stays a `def this(...)` delegating to it.
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val (loweredBody, superArgs) = (lowerCtors(cd.body, plan), plan.superArgs)
    val pparams = plan.primaryParams
    // A SYNTHESISED primary (CtorFunnel.Plan.synthetic) has no java constructor behind it, so its
    // parameters are rendered from the plan's own (name, type) pairs rather than from symbols.
    //
    // It is `protected`, and the reason is a corrected fact. This comment used to assert that
    // "scala's `extends C(args)` can only ever invoke C's PRIMARY, so hiding it would make the class
    // unextendable" — which is FALSE, and was the only thing keeping a constructor java never
    // declared in the published API. Compiled and run: a `private` primary with three secondaries is
    // reached by `class D extends p.C("hello")`, `class E extends p.C()` and `class F(k: Int) extends
    // p.C(k.toString)` from ANOTHER package; a `protected` primary is reached DIRECTLY by a
    // subclass's `extends` clause in another package, and by an anonymous `new G(3, false) {}`.
    //
    // `protected` rather than `private` because `private` is CLASS-private in scala, not
    // package-private: a SAME-package subclass sees only the nilary secondary ("too many arguments
    // for constructor A ... : (): g.A"). Choosing between them per class would mean asking "is this
    // class extended?", which is the whole-program question `ENGINE-LIMITS.md` D4 measures as drift
    // — and it is asked at emission, one module at a time, so a dependent would answer it
    // differently from the base. `protected` needs no such question, cannot be reached by ordinary
    // client code, and is what the reference ports write on every funnel class that is subclassed.
    // Bare `protected`, never `protected[pkg]`: a package qualifier would deny exactly the
    // cross-module subclassing this choice exists to permit (DESIGN.md §8.11).
    //
    // A DISAMBIGUATED synthesis takes one more parameter, of a marker type minted in this class's
    // own companion (`CtorFunnel.Plan.marker`). It is there to change the primary's ARITY, which is
    // what removes it from every `this(<a root's super arguments>)` overload candidate set at once
    // — `ENGINE-LIMITS.md` C8, where a real constructor narrower than the parent's formals won the
    // call and delegated to itself. The type is named through the companion's VALUE path, and it is
    // `protected` there, never `private`: scala requires every type in a member's signature to be
    // at least as visible as the member (C9).
    val markerParam = plan.marker.map(n => s"ctor$$: ${typeValue(cd.symbol)}.${esc(n)}").toList
    // …and the CONTEXT CLAUSE a phase put on this class's constructors (`CtorFunnel.Plan.givens`),
    // rendered as its own GROUP through `paramClause`. Java's parameter list is one list and
    // scala's is a list of groups; flattened into the value parameters the `using` is lost and the
    // class reads `class Scene($p: demo.Ctx)` — an ordinary parameter, no given in scope, every
    // `summon` in the body unresolved. That was one of `ENGINE-LIMITS.md` CT4's three causes, and it
    // is the one that lived HERE: the other two were the funnel reading such a constructor as
    // paramful and declining to promote it. Empty for every port that threads nothing, which is why
    // no emitted byte moves.
    val givenClause = plan.givens.map(paramClause).mkString
    // A PROMOTED java constructor is still a java DECLARATION, so §8.7's mapping governs it exactly
    // as it governs the `def this` secondaries — one rule per kind of declaration (§8.11). The
    // SYNTHESISED primary above is the deliberate exception: it is not a java declaration at all,
    // and bare `protected` is the pair of answers it needs — wider in the subclass direction, so a
    // dependent module in another package can still extend the class, and narrower in the package
    // direction, where nothing legitimate calls it but this class's own secondaries.
    val ctorVis = plan.primary.map(pc => vis(sym(pc.symbol), privateQualifier(s.owner))).getOrElse("")
    // …and a promoted parameter the java constructor ASSIGNS TO is a `var`.
    //
    // A java constructor parameter is an ordinary LOCAL and may be reassigned; a scala class
    // parameter is a `val`. Promoted unchanged, `C(int x) { x = x * 2; this.f = x; }` emits
    // `x$p = x$p * 2` — `E052 Reassignment to val`, loud but uncounted, because no library in this
    // corpus happens to write it. A record's COMPACT constructor is the shape that makes it
    // ordinary rather than exotic: JLS 8.10.4 exists PRECISELY so a record can normalise its
    // components by assigning the parameters, and the appended field assignments then read what the
    // body left.
    //
    // `private var` and not `var`: java's parameter is not a member at all, so the promotion must
    // not put a name on the emitted surface. Class-private is enough for every reference there can
    // be — they are all inside the class that promoted the constructor — and it keeps the header's
    // arity, its types and its descriptor exactly as they were.
    //
    // Decided from the LOWERED body, which is where the promoted statements are (`plan.primaryBody`
    // is only half of the picture once `lowerCtors` has run), and by SYMBOL rather than by name
    // (§4.56). Every write in this IR is a `Tree.Assign` — the frontend desugars `x *= 2`, `x++`
    // and `--x` into one — so the scan is complete.
    val mutatedParams: Set[SymId] =
      if pparams.isEmpty then Set.empty
      else
        val ps  = pparams.map(_.symbol).toSet
        val acc = collection.mutable.Set.empty[SymId]
        val scan = new Phase:
          def name: String = "emit/mutated-primary-params"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              case Tree.Assign(Tree.Ident(sy, _, _), _, _, _, _) if ps(sy) => acc += sy
              case _                                                    => ()
            t
        StandardTraversal.mapClassDef(scan, cd.copy(body = loweredBody))(using source)
        acc.toSet
    def primaryParam(v: Tree.ValDef): String =
      if mutatedParams(v.symbol) then s"private var ${param(v)}" else param(v)
    val prim    =
      if plan.isSynthesised then
        s" protected (${(plan.synthetic.map((n, t) => s"$n: ${tpe(t)}") ++ markerParam).mkString(", ")})$givenClause"
      else if pparams.nonEmpty then s"${if ctorVis.isEmpty then "" else " " + ctorVis}(${pparams.map(primaryParam).mkString(", ")})$givenClause"
      // a class whose constructor java declared NILARY and the pipeline gave a clause: the clause is
      // the whole parameter list, and `class C(using T)` is what puts the given in scope for the
      // body, the field initialisers and the `extends` clause at once.
      // …and a NILARY constructor that is not public needs somewhere for the modifier to sit. With
      // a context clause that place already exists and the clause must NOT gain an empty group
      // before it — `()(using Ctx)` is a different signature from `(using Ctx)` and every call site
      // would have to change. Without one, `class C private[p] ()` is the only spelling there is.
      else if ctorVis.isEmpty then givenClause
      else if givenClause.nonEmpty then s" $ctorVis$givenClause"
      else if kw == "class" then s" $ctorVis()"
      else givenClause
    // Does the emitted class have a PARAMFUL primary? A synthesised primary is one even though no
    // java constructor backs it, so `plan.primaryParams` is empty for it — reading only that told
    // `orderBody` the primary was nilary, and it then discarded the class's own no-arg constructor
    // as degenerate. `AlgorithmPath()` / `Synth()` simply vanished, and `new AlgorithmPath()` was a
    // compile error at every call site while `Plans.superCall` reported that same root EXPRESSED.
    val paramfulPrimary = plan.isSynthesised || pparams.nonEmpty
    // …and did the header just built KEEP the class's context clause? Asked of the rendered text
    // and not of `plan.givens`, because a clause the plan holds and the rendering flattens into a
    // value parameter is exactly the shape CT4 measured. A `trait` reaches this with no clause on
    // purpose — `CtorFunnel.classGivens` refuses one, since scala's trait parameters are a
    // different feature and the port's `promoteToClass` is the answer — and is counted here.
    checkClause(cd, rendered = prim.contains("(using "), form = kw)
    val superTpe = cd.parents.headOption.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    // A class-to-trait converted parent has no constructor, so super args must NOT be rendered
    // on the extends clause even when the funnel's plan carries them (the funnel promoted the
    // widest super-calling constructor as primary so its params become class params, but the
    // super args target a trait's abstract vals, not a constructor). Without this guard the
    // emitter renders `extends Pool(cap$p, max$p)` — `Pool does not take parameters`.
    def parentIsTrait: Boolean =
      def headSym(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
      superTpe.flatMap(headSym).flatMap(program.symbolOf).exists(_.flags.isTrait)
    val parents = cd.parents.map(parent).filter(_.nonEmpty) match
      case Nil                          => Nil
      case h :: t if superArgs.nonEmpty && !parentIsTrait =>
        val as = superArgs.zipWithIndex.map((a, n) => superArg(superTpe.getOrElse(TypeRepr.NoType), a, n, i))
        s"$h(${as.mkString(", ")})" :: t
      case all                          => all
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    // an all-static utility class (no instance state, no supertype) is just an `object` — so its
    // static members and nested types live together and see each other by simple name.
    val hasInstanceState = cd.body.exists {
      case d: Tree.DefDef => sym(d.symbol).name != "<init>" && !sym(d.symbol).flags.isStatic
      case v: Tree.ValDef => !sym(v.symbol).flags.isStatic
      case _              => false
    }
    // an all-static class can only collapse to an `object` if nobody EXTENDS it (you can't extend
    // an object), nobody INSTANTIATES it (you can't `new` one) and nobody NAMES IT AS A TYPE (an
    // object is a value, and no value is a type) — otherwise it stays a `class` with its statics in
    // a companion object.
    if kw == "class" && parents.isEmpty && cd.body.nonEmpty && !hasInstanceState && pparams.isEmpty &&
       !extendedTypes(cd.symbol) && !instantiatedTypes(cd.symbol) && !typeNamedElsewhere(cd.symbol) then
      val members = cd.body.filterNot { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
      val ob0 = classBodyStats(orderBody(members, cd.symbol, paramfulPrimary), plan, i + 1).filter(_.nonEmpty).mkString("\n")
      val ob  = if bnote.isEmpty then ob0 else s"$bnote\n$ob0"
      // the VISIBILITY only — an `object` takes no `abstract`, and `final object` is redundant.
      // THE COLLAPSE, recorded where it is TAKEN. A consumer that names this type in a type
      // position is naming a value, and nothing else in any artifact says so: `members.tsv` records
      // its kind as `class` (`ENGINE-LIMITS.md` D6's cross-module face). Recorded here rather than
      // re-derived because the four whole-program reads above exist only in this branch.
      recordTypeShape(cd, "object", plan, companion = false, statics = Nil)
      // an `object` has no constructor at all, so a context clause on this class's constructors has
      // nowhere to go here — counted rather than silently dropped (CT5).
      checkClause(cd, rendered = false, form = "object")
      return s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}object ${esc(s.name)}$tps {\n$ob\n${ind(i)}}"
    // Java statics have no instance home in Scala — they move to the companion object.
    val (statics, instance0) = if s.flags.isModule then (Nil, loweredBody) else loweredBody.partition(isStatic)
    // T22 — an `@interface`'s ELEMENTS (JLS 9.6.1), which are the whole of its instance side. They
    // become the emitted class's CONSTRUCTOR PARAMETERS, so they are taken out of the body here,
    // BEFORE `memberStat` runs: rendered as members they were emitted into a `body` the annotation
    // arm below then discards, which left four planned-and-never-written slots per port — one
    // `!! UNLOCATABLE` row each, under a key whose owner had been composed twice, and a javadoc the
    // trivia backstop then relocated because its declaration was not there (§4.58's recovery lane
    // reading high for a category that still wants a home). Both are that discarded rendering, not
    // two defects.
    val (annotElems, instance) =
      if !s.flags.isAnnotation then (Nil, instance0)
      else instance0.partition {
        case d: Tree.DefDef => sym(d.symbol).name != "<init>" && d.paramss.forall(_.isEmpty)
        case _              => false
      }
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body1   = joinStats(classBodyStats(orderBody(instance, cd.symbol, paramfulPrimary), plan, i + 1).filter(_.nonEmpty))
    // K22 — the CLASS-INITIALISATION trigger, ahead of every other class-body statement because
    // that is where java ran it. See [[forceCompanion]]; `statics` is where BOTH halves of java's
    // class initialiser lower to — the `static { }` blocks and the static field initialisers — so
    // this is asked of the very list that carries the defect.
    // …and only where there is a CONSTRUCTOR to hang it on. A `trait` body statement runs at every
    // implementor's initialisation, which is MORE than java does (JLS 12.4.1 does not initialise an
    // interface when an implementor is initialised), and the annotation rendering below emits no
    // body at all. Neither shape can arise from java — an interface may not declare a static
    // initialiser (JLS 9.1.1) — so both are left to `class-init-trigger` rather than guessed at.
    // …and never where forcing would RE-ENTER an initialisation already in progress: java tolerates
    // a cyclic pair of class initialisers and a scala companion does not, so the trigger is
    // declined and `class-init-trigger` counts the refusal (`ENGINE-LIMITS.md` K22 face 2).
    val force   = if hasClinit(statics) && kw == "class" && !s.flags.isAnnotation &&
                     !reentrantBearers.contains(cd.symbol)
                  then forceCompanion(cd, cd.symbol, balticporter.tir.ClassInitTriggerCheck.Instantiation, i + 1)
                  else ""
    // C3 item 4 — parent secondary's post-body, guarded by a null check. // ENGINE-LIMITS C3
    val postBody = currentClass.map(plans.primaryPostBodyFor(_)).getOrElse(Nil)
    val postBodyStr =
      if postBody.isEmpty || plan.postBodySlots.isEmpty then ""
      else
        val guardParam = plan.postBodySlots.head._1
        val pbStats = postBody.map(stat(_, i + 2)).filter(_.trim.nonEmpty)
        if pbStats.isEmpty then ""
        else s"${ind(i + 1)}if ($guardParam != null) {\n${pbStats.mkString("\n")}\n${ind(i + 1)}}"
    // JS-C43 — the members javac derives from a record header, which no java declaration carries.
    val (recMembers, recStatics, recNote) = recordMembers(cd, s, i)
    val body0   = joinStats(List(bnote, force, postBodyStr, body1, recMembers.mkString("\n")).filter(_.nonEmpty))
    val diamonds = diamondOverrides(cd, i + 1)
    val body    = if diamonds.isEmpty then body0 else joinStats(List(body0).filter(_.nonEmpty) ++ diamonds)
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    // Scala (unlike Java) forbids a NON-private member from referring to a bare-`private` type in
    // its signature — a public `Values extends MapIterator` / field `pool: ModelInstancePool` where
    // the referent is private is an error. Java nested classes leak this way constantly, which is
    // why this whole modifier used to be ERASED at the class header. It is not erased any more:
    // the rule is about UNQUALIFIED `private` only, and every rendering §8.7 gives a nested type is
    // QUALIFIED (`private[TopLevel]` for a java `private` one, `private[pkg]` for a package-private
    // one) — a public member may expose such a type in its signature, and a cross-package caller
    // may call it and hold the value. The erasure was therefore hiding a real level, which is what
    // the type-level half of §8.7's mapping restores. A top-level java type is never `private`, so
    // the bare form the sentence above is about cannot arise from this path at all.
    // A Java `@interface` is an ANNOTATION TYPE. Emitted as an ordinary interface it becomes a
    // trait, and then nothing can be annotated with it — 161 errors' worth of `@Null` in this
    // corpus alone. Scala's equivalent is a class extending `StaticAnnotation`.
    // The PROMOTED constructor's own Javadoc has no `def` left to sit on — `CtorFunnel` turned it
    // into the class's parameter list — so it joins the class's, which is where Scala documents a
    // primary constructor anyway. Without this it is simply dropped, and `TriviaCheck` counted it:
    // 138 Javadoc losses on libGDX core, the largest single category, most of them exactly this.
    // exactly the constructor `lowerCtors` replaces with its body, so this can never duplicate a
    // doc that is still attached to a `def this` somewhere in the class.
    val ctorLead = plan.primary.toList.flatMap(_.leading)
    // …and its NOTES go the same way, for the same reason and by §4.575's own rule. A PROMOTED
    // constructor has no `def` left for an `AtDeclaration` note to sit above, so a decision
    // subjected at it — a SAM conversion inside a constructor body, a substituted call, any kind in
    // that set — simply produced NO NOTE: `NoteCoverageCheck` reported `decision with no note` and
    // nothing else in the run could see it (measured at 1 on the libGDX base, `ENGINE-LIMITS.md`
    // I9). The class is where scala documents a primary constructor, which is where the javadoc
    // above already goes.
    val ctorNote = plan.primary.toList.map(c => declNotes(c.symbol, i)).mkString
    // JS-C44 — note placed after cnote per §4.575 order.
    val (seal, sealNote) = sealOf(cd, s, i)
    // T22 — `@interface` elements become the class's parameter list (a `val`, keeping java's
    // element name); a read becomes the field selection `applyStr0` renders parenless. No JVM
    // retention: `getAnnotation` still cannot recover one reflectively.
    val annotElemParams = annotElems.collect { case d: Tree.DefDef =>
      val nm  = esc(sym(d.symbol).name)
      val df  = d.rhs.map(r => s" = ${term(r, i)}").getOrElse("")
      s"val $nm: ${tpe(d.returnTpt.tpe)}$df"
    }
    val annotPrim = if annotElemParams.isEmpty then prim else s"(${annotElemParams.mkString(", ")})$prim"
    // each element's Javadoc joins the class's, since scala documents a primary ctor's params there.
    val annotLead = annotElems.flatMap { case d: Tree.DefDef => d.leading; case _ => Nil }
    val cls     =
      if s.flags.isAnnotation then
        s"${leading(cd.leading ++ annotLead, i)}$cnote${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$annotPrim extends scala.annotation.StaticAnnotation"
      else s"${leading(cd.leading ++ ctorLead, i)}$cnote$ctorNote$sealNote$recNote${annots(s, i)}${ind(i)}${mods(s, privateQualifier(s.owner))}$seal$abs$kw ${esc(s.name)}$tps$prim$ext$open"
    // Java interface/parent CONSTANTS are `static`, so they live in the parent's companion object
    // — which Scala does NOT inherit. Re-export each static-bearing parent's companion so an
    // inherited constant accessed via a subclass (`GL30.GL_LUMINANCE`, declared in `GL20`) resolves.
    // exclude the class's OWN static names from the re-export (a subtype may redeclare a parent
    // constant — OpenGL's GL31 vs GL30 — which would otherwise be a duplicate/conflicting export).
    //
    // A STATIC INITIALIZER BLOCK is not one of those names. Java calls it `<clinit>` — the JVM's
    // name for the synthetic method it compiles a `static { … }` block into — and no Scala
    // identifier can spell it, backticks included: there is no member at that name to hide, so an
    // exclusion naming it is not merely useless, it is `export P.{<clinit> => _, *}`, which the
    // parser reads as an XML start tag. The block has no name in the emitted Scala either
    // ([[isInitBlock]] lowers it into the companion's body), so it can never collide with an
    // inherited constant and has nothing to exclude.
    //
    // Invisible for six ports because it needs BOTH halves at once — a class that inherits statics
    // from a parent AND declares a `static { }` block of its own. libGDX core has 605 types and not
    // one of them; gdx-gltf's attribute hierarchy has three (`PBRColorAttribute`,
    // `PBRCubemapAttribute`, `PBRTextureAttribute`, each `extends` a libGDX `Attribute` subclass
    // whose constants it re-exports, each registering its own aliases in a `static { }`).
    val ownStaticNames = statics.collect {
      case d: Definition if !d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef]) =>
        esc(sym(d.symbol).name)
    }.distinct
    // Two exports must not both deliver the same name. `GL20Interceptor extends GLInterceptor with
    // GL20` and `GLInterceptor` itself implements `GL20`, so `GLInterceptor`'s companion ALREADY
    // re-exports `GL20`'s constants by this rule — a second `export GL20.*` is a duplicate
    // definition, not extra reach. Drop a parent another exported parent wholly subsumes, and for
    // the DIAMOND that remains (`GLInterceptor` and `GL30` meeting at `GL20`) exclude, from each
    // later export, every name an earlier one already delivered FROM THE SAME DECLARING TYPE. The
    // same-owner test is what keeps this safe: a genuine redeclaration (`GL31` shadowing a `GL30`
    // constant) has a different owner, so it is never silently merged away.
    val exported       = parentSymsOf(cd).filter(p => staticsReachable(p))
    val kept           = exported.filterNot(p => exported.exists(q => q != p && ancestorsOf(q).contains(p)))
    val delivered      = kept.map(staticOwnersOf(_))
    val extraExcl      = Array.fill(kept.size)(Set.empty[String])
    delivered.flatMap(_.keys).distinct.foreach { n =>
      val at = delivered.indices.filter(j => delivered(j).contains(n)).toList
      if at.sizeIs > 1 then
        val owners = at.map(delivered(_)(n)).distinct
        // Same owner everywhere ⇒ the SAME constant arriving twice; keep the first export and drop
        // the rest. Different owners ⇒ a real redeclaration, and the one Java resolves to is the
        // most specific — the owner that descends from all the others. Incomparable owners are
        // ambiguous in Java too, so keep the first and let the redeclaration be the loser rather
        // than guess.
        val winner = at.find(j => owners.forall(o => o == delivered(j)(n) || ancestorsOf(delivered(j)(n)).contains(o)))
          .getOrElse(at.head)
        at.filter(_ != winner).foreach(j => extraExcl(j) = extraExcl(j) + n)
    }
    val parentExports  = kept.zipWithIndex.map { (p, j) =>
      val excluded = (ownStaticNames.toSet ++ extraExcl(j) ++ nonPublicStatics(delivered(j))).toList.sorted
      val sel      = if excluded.isEmpty then "*" else s"{${excluded.map(_ + " => _").mkString(", ")}, *}"
      s"${ind(i + 1)}export ${typeValue(p)}.$sel"
    }
    // the disambiguator's marker type, minted in THIS class's companion — one line, and the reason
    // it is here rather than in `runtime/` is that emitted code then carries no dependency on the
    // engine's runtime artifact for a purely local encoding (`DESIGN.md` §8.2). A class that needs
    // one may have no companion at all, so the companion is emitted for it.
    val markerDecl = plan.marker.toList.map(n => s"${ind(i + 1)}protected final class ${esc(n)}")
    // …and the record's EXTRACTOR, which is the one member of JS-C43's synthesis with no home in
    // the class. A record with no statics has no companion at all, so it is emitted for it — the
    // marker declaration above is the same shape and the same reason.
    val hasCompanion = !(statics.isEmpty && parentExports.isEmpty && markerDecl.isEmpty && recStatics.isEmpty)
    // …and the OTHER three forms, recorded from the values that decided them. `companion` and
    // `statics` are the two an `export Base.*` in a dependent has to read rather than recompute from
    // the base's Java: a base with no companion makes the export an error outright, and a static
    // the base renamed or moved is named wrongly by any recomputation.
    recordTypeShape(cd,
      form      = if s.flags.isAnnotation then "annotation" else kw,
      plan      = plan,
      companion = hasCompanion,
      statics   = ownStaticNames)
    if !hasCompanion then cls
    else
      // K22's SECOND trigger — JLS 12.4.1 item 7. Initialising a class initialises its SUPERCLASS
      // first, and what initialises a class with nothing instantiating it is a bare `S.member`
      // read; in Scala that touches `object S` and reaches no other object, so an ancestor's
      // `static { }` never runs on that path. The force goes FIRST in the companion body, because
      // java ran the ancestor's initialiser before this type's own static field initialisers.
      //
      // The companion is the whole condition — an object that is never initialised runs nothing, so
      // a line inside one can never over-trigger relative to java, whatever put the object there.
      // That is why this asks `hasCompanion` rather than re-deriving "does anything read a static
      // of this type", which is the string-shaped guess §4.56 is about.
      val superForce = nearestClinitAncestor(cd.symbol)
        .filterNot(reentrantBearers.contains)
        .map(a => forceCompanion(cd, a, balticporter.tir.ClassInitTriggerCheck.SubclassInit, i + 1))
        .toList.filter(_.nonEmpty)
      val sb = (superForce ++ parentExports ++ markerDecl ++
                orderBody(statics, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
                recStatics).mkString("\n")
      s"$cls\n${ind(i)}object ${esc(s.name)} {\n$sb\n${ind(i)}}"

  /** `this.x = x` — the NAME assigned, when the assignment is a field taking its own same-named
    * source and nothing else. Both sides must resolve to the same simple name and the right-hand
    * side must be a bare identifier, so `this.up = new Vector3(upX, …)` and `this.a = b` are not
    * this shape and are not touched. */
  private[emit] def selfAssignedParam(a: Tree.Assign): Option[String] =
    val lhs = a.lhs match
      case Tree.Select(_: Tree.This, m, _, _) => Some(sym(m).name)
      case Tree.Ident(m, _, _)                => Some(sym(m).name)
      case _                                  => scala.None
    val rhs = a.rhs match
      case Tree.Ident(m, _, _) => Some(sym(m).name)
      case _                   => scala.None
    lhs.filter(l => rhs.contains(l))

  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a
    * companion `object` holding each constant as a `case object` and a `values` array. */
  /** A java ENUM takes ONE of two shapes, and which one is a fact about java's own declaration —
    * [[balticporter.tir.EnumShape]], read here, at every `values()` call site and by
    * `OmissionCheck.enumShapeRefusals`, so the three can never disagree.
    *
    * The scala 3 `enum` is the shape that IS a `java.lang.Enum[X]`, which no `sealed abstract class`
    * may claim ("only enums defined with the enum syntax can"). Everything a bound like
    * `<E extends Enum<E> & BitField>` asks of a ported enum — and everything `EnumSet`, `EnumMap`
    * and `Comparable<E>` ask — is answered by that supertype and by nothing else.
    *
    * Where a constant carries a class BODY, or an emitted member would collide with one of the
    * members java made FINAL on `java.lang.Enum`, the `enum` form cannot express java's declaration
    * at all and the pre-existing sealed shape is kept — a REFUSAL, counted at
    * `OmissionCheck.enumShapeRefusals` rather than silently chosen. */
  private[emit] def enumDef(cd: Tree.ClassDef, i: Int): String =
    if balticporter.tir.EnumShape.isScalaEnum(program, cd) then scalaEnumDef(cd, i) else sealedEnumDef(cd, i)

  /** The parts BOTH enum shapes are made of, derived ONCE.
    *
    * The two arms differ in the header they write and in the members java.lang.Enum does or does not
    * supply; they do not differ about which constructor is the primary, which parameters it
    * promotes, which field a parameter supersedes or which of its statements survive. Read twice
    * those would be two derivations free to drift — the failure `CtorFunnel.enumPrimaryCtor` exists
    * to prevent one level down (§4.56). */
  private[emit] final case class EnumParts(ctorParams: List[Tree.ValDef], paramNames: Set[String],
                                     instance: List[Statement], ctorStats: List[Statement],
                                     statics: List[Statement], eprimary: String)

  private[emit] def enumParts(cd: Tree.ClassDef): EnumParts =
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the emitted type's primary constructor params (as
    // `var` fields), so `Nearest extends TextureFilter(GL_NEAREST)` has somewhere to pass its arg.
    // Drop the constructor itself and any field that a param supersedes (same name).
    // JAVA's parameters, never `paramss.flatten` (`CtorFunnel.valueParams`, and the same rule the
    // funnel applies one level up). A context clause a phase put on this constructor is not a java
    // parameter and cannot become a `var` field: the parameter is ANONYMOUS, so it would render as
    // `var : sge.Sge`, and an enum's primary is reached by every `case object` — each of which
    // would have to pass an argument for a clause the emitter has no way to supply. So it is
    // dropped from the parameter list and COUNTED as a lost clause instead (`ENGINE-LIMITS.md`
    // CT5); an enum whose body needs an ambient context is a port-level decision, not a rendering.
    // THE ROOT, never `ctors.head`. For the single-constructor enum every corpus library had, the
    // two are the same and nothing could tell them apart; for an overloaded one the head is
    // whichever java wrote first, and taking ITS parameters gave a delegating `Flags()` beside
    // `Flags(int)` an EMPTY primary — `case object X extends Flags(3)` is `too many arguments`, and
    // every constant that named the nilary overload silently got the field's DEFAULT where java ran
    // `this(1)`. `CtorFunnel.enumPrimaryCtor` is the shared derivation (§4.56) and
    // `OmissionCheck.overloadedEnumCtors` counts what it refuses.
    val primaryCtor = CtorFunnel.enumPrimaryCtor(program, cd)
    val ctorParams = primaryCtor.map(CtorFunnel.valueParams(program, _)).getOrElse(Nil)
    checkClause(cd, rendered = false, form = "enum")
    val paramNames = ctorParams.map(v => sym(v.symbol).name).toSet
    // WHICH field a parameter supersedes is `CtorFunnel`'s to answer, matched on the name AND the
    // TYPE: java's two variable scopes let a constructor parameter name a field it is not and then
    // COMPUTE that field from it, and a name test drops the field and emits the parameter's type in
    // its place. `funnelParamRenames` reads the same function, so the field that survives here is
    // exactly the one the parameter was renamed out of the way of (§4.56).
    val superseded = CtorFunnel.enumSupersededFields(program, cd)
    val instance   = instance0.filterNot {
      case d: Tree.DefDef => sym(d.symbol).name == "<init>"
      case v: Tree.ValDef => superseded(v.symbol)
      case _              => false
    }
    // …and the self-assignment drop follows the SAME set, not `paramNames`. `this.f = f` is
    // redundant only because the promotion performed it; where the field survives, the assignment is
    // what fills it and dropping it would leave the field at its default, silently.
    val supersededNames = instance0.collect {
      case v: Tree.ValDef if superseded(v.symbol) => sym(v.symbol).name
    }.toSet
    // the ctor BODY must run too, not just the params, or a field it assigns stays at its default.
    // CtorFunnel is not consulted: an enum's shape is fixed (the sealed class's primary IS the java
    // ctor), so the lowering runs off the ROOT — the one non-delegating overload.
    val ctorStats =
      primaryCtor.toList.flatMap(CtorFunnel.stmtsOf).filterNot {
        // java's implicit super(), reaching java.lang.Enum with no expression here.
        case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"
        // this.glEnum = glEnum is a self-assignment the promotion already performs; drop only that
        // exact shape (an assignment that computes anything stays).
        case a: Tree.Assign                                  => selfAssignedParam(a).exists(supersededNames)
        case _                                               => false
      }
    // A superseded field's modifiers come with its value: the parameter is emitted AS that field,
    // so its access level, `private[Enum]` qualification (java's private reaches the whole
    // top-level enclosure; scala's bare `private` on a class param does not) and `val`-vs-`var`
    // (decided by whether the body still writes it) all carry over from the field, never from the
    // parameter (which has none of them — JLS 8.8.1). A parameter superseding nothing keeps `var`.
    val standsFor: Map[SymId, SymId] =
      val dropped = instance0.collect { case v: Tree.ValDef if superseded(v.symbol) => v.symbol }.toSet
      CtorFunnel.enumSupersededBy(program, cd).filter((_, f) => dropped(f))
    // every write the EMITTED body still performs, to the field or to the parameter that replaced
    // it — the dropped self-assignment is already gone from `ctorStats`, so it cannot vote here.
    val writtenAfterPromotion: Set[SymId] =
      if standsFor.isEmpty then Set.empty
      else
        val watched = standsFor.keySet ++ standsFor.values
        val acc     = collection.mutable.Set.empty[SymId]
        val scan = new Phase:
          def name: String = "emit/written-enum-params"
          override def transformTerm(t: Term)(using Program): Term =
            t match
              case Tree.Assign(Tree.Ident(sy, _, _), _, _, _, _) if watched(sy)                 => acc += sy
              case Tree.Assign(Tree.Select(_: Tree.This, sy, _, _), _, _, _, _) if watched(sy)  => acc += sy
              case _                                                                          => ()
            t
        StandardTraversal.mapClassDef(scan, cd.copy(body = instance ++ ctorStats))(using source)
        acc.toSet
    // the qualifier a bare `private` takes here — the enclosing top-level type where there is one
    // (`privateQualifier`'s own answer for a NESTED enum), and otherwise the enum itself.
    val es           = sym(cd.symbol)
    val enumPrivateIn = privateQualifier(es.owner).orElse(Some(esc(es.name)))
    def enumParam(v: Tree.ValDef): String =
      val nm = esc(sym(v.symbol).name)
      val ty = tpe(v.tpt.tpe)
      standsFor.get(v.symbol) match
        case Some(f) =>
          val fs = sym(f)
          val kw = if fs.flags.isMutable || writtenAfterPromotion(f) || writtenAfterPromotion(v.symbol)
                   then "var" else "val"
          s"${vis(fs, enumPrivateIn)}$kw $nm: $ty"
        case None => s"var $nm: $ty"
    val eprimary = if ctorParams.isEmpty then "" else s"(${ctorParams.map(enumParam).mkString(", ")})"
    EnumParts(ctorParams, paramNames, instance, ctorStats, statics, eprimary)

  /** THE PRE-EXISTING SHAPE — a `sealed abstract class` plus one `case object` per constant.
    *
    * It is what a java enum the scala 3 `enum` cannot express is emitted as, and the ONE thing it
    * does not do is extend `java.lang.Enum[X]` (scalac: "only enums defined with the enum syntax
    * can"), which is why `name()`, `ordinal()`, `values()` and `valueOf` are all supplied by hand
    * below. `EnumShape.refusal` decides which enums arrive here and `OmissionCheck` counts them. */
  private[emit] def sealedEnumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val parts   = enumParts(cd)
    import parts.{ctorParams, paramNames, instance, ctorStats, statics, eprimary}
    // Java's final Enum.name() — a case object's toString IS its declared name, so name() returns
    // it. A promoted ctor parameter counts as a declared `name` too (CLAUDE.md §4.55).
    val hasName = paramNames("name") ||
      instance.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }
    val nameM   = if hasName then Nil else List(s"${ind(i + 1)}def name(): java.lang.String = this.toString()")
    // Java's final Enum.ordinal() — emitted as an abstract member with one override per constant
    // (O(1), matching java) rather than derived from values().indexOf(this).
    val hasOrdinal = paramNames("ordinal") ||
      instance.exists { case d: Definition => sym(d.symbol).name == "ordinal"; case _ => false }
    val ordinalM = if hasOrdinal then Nil else List(s"${ind(i + 1)}def ordinal(): scala.Int")
    val cnote   = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote   = bodyNotes(cd.symbol, i + 1)
    // The constructor's statements go LAST among the class body's own, after every declaration:
    // a Scala class body runs its statements in textual order, so an assignment placed above the
    // `var` it targets would not compile, and one placed below runs exactly where java ran it.
    val members = List(bnote).filter(_.nonEmpty) ++
      orderBody(instance, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
      ctorStats.map(memberStat(_, i + 1)).filter(_.nonEmpty) ++ nameM ++ ordinalM
    val cbody   = members.mkString("\n")
    // §8.7 governs the enum TYPE; its constructor is left public — java's implicit `private`
    // (JLS 8.9.2) has no declaration left to carry it, since the params ARE the primary.
    val cls     = s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}sealed abstract class $name$eprimary$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.zipWithIndex.map { (ec, idx) =>
      val cn   = esc(sym(ec.symbol).name)
      // the ROOT's arguments: a constant naming a delegating overload passes what that overload's
      // this(...) passes, since the emitted primary IS the root.
      val cargs = CtorFunnel.enumConstantArgs(program, cd, ec.ctorArgs).getOrElse(ec.ctorArgs)
      val args = if cargs.isEmpty then "" else s"(${cargs.map(term(_, i + 1)).mkString(", ")})"
      // the constant's own members first, then the `ordinal()` this lowering owes the base.
      val stats = ec.body.map(stat(_, i + 2)) ++
        (if hasOrdinal then Nil else List(s"${ind(i + 2)}override def ordinal(): scala.Int = $idx"))
      val body = if stats.isEmpty then "" else s" {\n${stats.mkString("\n")}\n${ind(i + 1)}}"
      s"${leading(ec.leading, i + 1)}${ind(i + 1)}case object $cn extends $name$args$body"
    }
    // `def` (not `val`) so Java's `E.values()` call site type-checks; also a no-paren read works.
    val values = s"${ind(i + 1)}def values(): scala.Array[$name] = scala.Array(${cd.enumCases.map(ec => esc(sym(ec.symbol).name)).mkString(", ")})"
    // Java's `Enum.valueOf(String)` — resolve a constant by name (throws like the JDK on no match).
    val vArms  = cd.enumCases.map(ec => esc(sym(ec.symbol).name)).map(n => s"""${ind(i + 2)}case "$n" => $n""").mkString("\n")
    val valueOf = s"${ind(i + 1)}def valueOf(name: java.lang.String): $name = name match {\n$vArms\n${ind(i + 2)}case _ => throw new java.lang.IllegalArgumentException(name)\n${ind(i + 1)}}"
    val objBody = (cases :+ values :+ valueOf) ++ statics.map(memberStat(_, i + 1)).filter(_.nonEmpty)
    // sealed abstract class + companion of case objects; CtorFunnel is not consulted for an enum,
    // so the primary IS the java constructor and its slots are ctorParams.
    recordedTypeShapes(s.fullName) = Surface.TypeShape(
      form        = "enum-class",
      companion   = true,
      statics     = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct,
      primary     = Some(Descriptor(ctorParams.map(v => descriptorParam(v.tpt.tpe)))),
      primaryKind = "not-funnelled",
      primaryVis  = "public",
      parents     = parentSymsOf(cd).map(p => sym(p).fullName),
      flags       = List("sealed", "abstract"),
    )
    s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  /** `enum X(…) extends java.lang.Enum[X] with …` — the shape used where a caller depends on the
    * `java.lang.Enum` supertype itself (a bound, `EnumSet`/`EnumMap`, `Comparable`, `isEnum`).
    * `name()`/`ordinal()` are FINAL there and `values`/`valueOf` come from the `enum` desugaring, so
    * `EnumShape.Reserved` refuses an enum whose own declaration needs one of those names
    * (CLAUDE.md §4.55, ENGINE-LIMITS T11). `values` renders parenless here; `applyStr0` matches. */
  private[emit] def scalaEnumDef(cd: Tree.ClassDef, i: Int): String =
    val s     = sym(cd.symbol)
    val name  = esc(s.name)
    // java.lang.Enum[X] first, then every interface parent (JLS 8.9 — enums may implement only),
    // deduplicating one the frontend already carried as java.lang.Enum.
    val mixins = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext    = s" extends java.lang.Enum[$name]" + mixins.map(p => s" with $p").mkString
    val parts  = enumParts(cd)
    import parts.{ctorParams, instance, ctorStats, statics, eprimary}
    val cnote  = if cd.symbol == currentTopLevelSym then "" else declNotes(cd.symbol, i)
    val bnote  = bodyNotes(cd.symbol, i + 1)
    // cases go last: the desugaring lifts them out of the template, so their position among the
    // real statements is free — keep it after members to match the sealed arm's body order.
    val cases = cd.enumCases.map { ec =>
      val cn = esc(sym(ec.symbol).name)
      val cargs = CtorFunnel.enumConstantArgs(program, cd, ec.ctorArgs).getOrElse(ec.ctorArgs)
      val args  = if cargs.isEmpty then "" else s"(${cargs.map(term(_, i + 1)).mkString(", ")})"
      // `case A extends X(…)`, one spelling per constant, so each constant keeps its own comment.
      s"${leading(ec.leading, i + 1)}${ind(i + 1)}case $cn extends $name$args"
    }
    val members = List(bnote).filter(_.nonEmpty) ++
      orderBody(instance, cd.symbol).map(memberStat(_, i + 1)).filter(_.nonEmpty) ++
      ctorStats.map(memberStat(_, i + 1)).filter(_.nonEmpty) ++ cases
    val cbody = members.mkString("\n")
    val cls   = s"${leading(cd.leading, i)}$cnote${ind(i)}${vis(s, privateQualifier(s.owner))}enum $name$eprimary$ext" +
      (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val objBody = statics.map(memberStat(_, i + 1)).filter(_.nonEmpty)
    // form = "enum", not "enum-class": the two shapes publish different surfaces, so base-surface
    // can see a dependent disagreement. companion = true unconditionally — the enum desugaring
    // always makes one, regardless of whether objBody (java's statics) is empty.
    recordedTypeShapes(s.fullName) = Surface.TypeShape(
      form        = "enum",
      companion   = true,
      statics     = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct,
      primary     = Some(Descriptor(ctorParams.map(v => descriptorParam(v.tpt.tpe)))),
      primaryKind = "not-funnelled",
      primaryVis  = "public",
      parents     = parentSymsOf(cd).map(p => sym(p).fullName),
      flags       = List("enum"),
    )
    if objBody.isEmpty then cls
    else s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  // a Java `static` nested class has no instance home in Scala → it moves to the companion
  // `object` alongside static vals/defs. A non-static inner class stays in the class body.
  /** Replace the constructor `CtorFunnel` promoted to Scala's PRIMARY by its own body statements,
    * which run at construction. `super(args)` is already in the `extends` clause and its params in
    * the class's parameter list; every other constructor stays a secondary `def this(...)`. */
  private[emit] def lowerCtors(body: List[Statement], plan: CtorFunnel.Plan): List[Statement] =
    plan.primary match
      case None    => body
      case Some(c) => body.flatMap { case d: Tree.DefDef if d.symbol == c.symbol => plan.primaryBody; case s => List(s) }

  /** the local `def` a PROMOTED constructor body carrying a `return` is wrapped in. Named per
    * class, not per program, so it cannot renumber under an unrelated edit (ENGINE-LIMITS M10). */
  private[emit] val CtorBodyName = "ctorBody$"

  /** JS-C51 — java `return` in a constructor leaves the constructor (JLS 14.17); promoted into the
    * class body it becomes `E091 return outside method definition`. Wrapped in a local `def` (a
    * `return`'s only valid target, and unlike `boundary.Break` not swallowed by a `catch
    * (Exception)` the promoted body may hold — §4.4), itself wrapped in a block so the `def` is not
    * a class member and does not appear in `members.tsv`. Only `plan.primaryBody` moves inside, at
    * the constructor's own position; fields and init blocks are already hoisted above it by
    * `orderBody` (§4.55). [[returnsIn]] stops at a nested `Tree.Lambda`/`DefDef`/`AnonClass`, so a
    * `return` belonging to an inner listener is not a reason to wrap. A value-carrying `return` is
    * refused and left as is (javac itself rejects it in a constructor). */
  private[emit] def classBodyStats(ordered: List[Statement], plan: CtorFunnel.Plan, i: Int): List[String] =
    val promoted = plan.primaryBody
    def inBody(s: Statement) = promoted.exists(_ eq s)
    if promoted.isEmpty || !returnsIn(promoted) || collectReturns(promoted).exists(_.expr.isDefined)
    then ordered.map(memberStat(_, i))
    else
      val first = ordered.indexWhere(inBody)
      ordered.zipWithIndex.flatMap { (s, k) =>
        if !inBody(s) then List(memberStat(s, i))
        else if k != first then Nil // rendered inside the wrapper, at the FIRST promoted statement
        else
          // rendered at i+2 here too, or srcMapOf's lookup-by-produced-text cannot locate it.
          val stats = ordered.filter(inBody).map(memberStat(_, i + 2))
          List(s"${ind(i)}{\n${ind(i + 1)}def $CtorBodyName(): scala.Unit = {\n" +
               s"${stats.mkString("\n")}\n${ind(i + 1)}}\n${ind(i + 1)}$CtorBodyName()\n${ind(i)}}")
      }

  /** a Java `static { … }` / instance `{ … }` initializer block, carried as a synthetic member. */
  private[emit] def isInitBlock(d: Tree.DefDef): Boolean =
    val n = sym(d.symbol).name
    n == "<clinit>" || n == "<initblock>"

  /** does this member list carry java CLASS INITIALISER content — JLS 12.4.2 step 9, never the
    * instance initialiser? Delegates to `ClassInitTriggerCheck.stepNine`, the one definition the
    * repair and its watchdog share — keyed on the `static { }` block alone this missed a
    * registration written as a static field initialiser (same `<clinit>` sequence). The constant
    * variable stays outside it: javac inlines it (JS-C08). */
  private[emit] def hasClinit(members: List[Statement]): Boolean =
    balticporter.tir.ClassInitTriggerCheck.stepNine(members)(using program)

  // K22 — a java class initialiser (JLS 12.4.2 step 9: static field initialisers + `static { }`
  // blocks, one sequence) runs at class initialisation; a scala companion initialises only when
  // something touches the OBJECT, which `new T(…)` does not. So it is emitted into the companion
  // and reproduced only at java's own trigger list (JLS 12.4.1): INSTANTIATION (forced ahead of
  // every field initialiser, at the class body's head), STATIC ACCESS (already an access to the
  // object, needs nothing), and SUBCLASS INITIALISATION (item 7 — force the nearest bearing
  // ancestor's companion). Never "call it from every use": java's constant-variable inlining means
  // a plain read triggers nothing, and forcing there would re-enter the Vector3/Matrix4 init cycle
  // (§4.4). REFLECTION cannot be reproduced (a reflective load of `T` does not touch `T$`) and is
  // stated rather than counted (ENGINE-LIMITS K22). `val _ = <fully-qualified path>`: bare would be
  // `E176 unused value` under the consumer's own `-Wall` (§4.45); qualified because an unqualified
  // simple name inside the body can resolve to a same-named MEMBER instead (§4.56).

  /** The note and statement that force `target`'s companion, recorded as one [[Decision]] about
    * `cd` so the note is DERIVED (§4.575) and `NoteCoverageCheck` sees the pair. `target` is `cd`
    * itself for the instantiation trigger, an ANCESTOR for the subclass one (JLS 12.4.1 item 7).
    * @param trigger which of JLS 12.4.1's actions this statement stands for. */
  private[emit] def forceCompanion(cd: Tree.ClassDef, target: SymId, trigger: String, i: Int): String =
    val s  = sym(cd.symbol)
    val tg = sym(target)
    val why =
      if target == cd.symbol then
        "java runs this class's initialiser — its `static { }` blocks and its static field " +
          "initialisers, one sequence, JLS 12.4.2 step 9 — at class initialisation, and a scala " +
          "companion initialises on first access to the OBJECT, which `new` is not"
      else
        s"initialising this type initialises `${tg.fullName}` first (JLS 12.4.1 item 7), which runs " +
          "that type's class initialiser; a scala object's initialisation reaches no other object"
    val d = Decision(
      kind       = Decision.Kind.ForcedClassInit,
      subject    = cd.symbol,
      subjectFqn = s.fullName,
      detail     = Map("trigger" -> trigger, "forces" -> tg.fullName, "why" -> why),
      reason     = Reason.Universal("class-init-trigger(§4.4)"),
      origin     = cd.origin,
    )
    emissionOf += d
    printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
    forcedClinits += (cd.symbol -> trigger)
    s"${PorterNote.render(d, ind(i))}${ind(i)}val _ = ${escPath(tg.fullName).replace('$', '.')}"

  /** every type this program declares whose class initialiser does anything — [[hasClinit]]'s
    * question (JLS 12.4.2 step 9), asked of the DECLARED body. */
  private[emit] lazy val clinitBearers: Map[SymId, Tree.ClassDef] =
    val acc = collection.mutable.Map[SymId, Tree.ClassDef]()
    allDeclaredClasses.foreach(cd => if hasClinit(cd.body) then acc(cd.symbol) = cd)
    acc.toMap

  /** …and the ones whose force would be RE-ENTRANT, which the repair declines and
    * `class-init-trigger` counts. The check's own function, so the refusal and the count cannot
    * disagree about which types it names (`ENGINE-LIMITS.md` K22 face 2). */
  private[emit] lazy val reentrantBearers: Map[SymId, SymId] =
    balticporter.tir.ClassInitTriggerCheck.reentrantBearers(program, clinitBearers)

  /** the ancestor edges JLS 12.4.1 item 7 traverses — the SUPERCLASS chain plus a superinterface
    * declaring a default method, and never `parentsBySym`, whose edges are every parent there is. */
  private[emit] lazy val item7ParentsBySym: Map[SymId, List[SymId]] =
    balticporter.tir.ClassInitTriggerCheck.item7Parents(program)

  /** the nearest ancestor of `s` carrying a class initialiser — the ONE this type's companion has
    * to force. Java initialises the whole superclass chain, and forcing only the nearest reproduces
    * that because THAT type's companion carries the same line for ITS own nearest, recursively.
    * Breadth-first, so "nearest" means nearest and not "first found down one branch". */
  private[emit] def nearestClinitAncestor(s: SymId): Option[SymId] =
    def go(front: List[SymId], seen: Set[SymId]): Option[SymId] =
      val next = front.flatMap(item7ParentsBySym.getOrElse(_, Nil)).filterNot(seen).distinct
      next.find(clinitBearers.contains) match
        case Some(a)         => Some(a)
        case _ if next.isEmpty => scala.None
        case _               => go(next, seen ++ next)
    go(List(s), Set(s))

  /** every (type, trigger) pair this emitter forced — the input to `class-init-trigger`, which
    * takes the CENSUS of `static { }` blocks from the trees itself. An empty set therefore
    * reproduces the un-repaired engine on the same trees, exactly as `switch-null` does. Keyed by
    * the TRIGGER as well as the type because the two are answered at different call sites and a
    * type covered for one is not covered for the other. */
  private[emit] val forcedClinits = collection.mutable.Set.empty[(SymId, String)]
  def forcedClassInits: Set[(SymId, String)] = forcedClinits.toSet

  private[emit] def isStatic(s: Statement): Boolean = s match
    case d: Tree.ClassDef => sym(d.symbol).flags.isStatic
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields first,
    * then constructors in DELEGATION-TOPOLOGICAL order (each ctor's `this(args)` target emitted
    * before it), then everything else. Arity is not a reliable proxy — a 3-arg convenience ctor can
    * delegate to a 1-arg one (`Texture(pixmap,fmt,mip)` → `Texture(data)`), so we follow the actual
    * `this(...)` edges, keyed by the target ctor's own symbol.
    *
    * `owner` is the class whose body this is, and it decides WHICH `ValDef`s the hoist applies to —
    * `ENGINE-LIMITS.md` C12. Two kinds of `ValDef` reach this list and they are the same node kind:
    *
    *  - the class's own FIELDS, which java runs in step 4 of JLS 12.5 — in textual order, before
    *    any constructor body statement. Hoisting them puts every one ahead of the promoted body,
    *    which is where java runs them, and a field declared BELOW the constructor needs the hoist
    *    to compile at all;
    *
    *    **…but step 4 is not only fields, and "whatever order the java file declared them in" was
    *    an overclaim.** JLS 12.5 step 4 runs field initialisers and INSTANCE INITIALISER BLOCKS as
    *    ONE sequence, in textual order (12.4.2 step 9 says the same of the static pair). A block is
    *    carried as a synthetic `<initblock>`/`<clinit>` member — a `Tree.DefDef`, not a `ValDef` —
    *    so it fell into `rest`, behind every field: `{ b = 2; } int b = 5;` left `b == 2` where
    *    java leaves 5, because the assignment java ran FIRST ran LAST. Same evidence as C12 — valid
    *    Scala, no compile error, no check count, only a run can see it — which is why the hoisted
    *    group is "step-4 members" and their RELATIVE ORDER is java's, rather than "the `ValDef`s";
    *  - a PROMOTED CONSTRUCTOR LOCAL, spliced in by [[lowerCtors]] as part of `plan.primaryBody`.
    *    That declaration is a step-5 constructor BODY statement: java ran it exactly where it stood,
    *    among the constructor's other statements, and the interleaving is what carries every
    *    dependency between them. Hoisted, it initialises itself before the statements java ran
    *    first — measured on liqp's `Template` as 409 of 414 test failures, all `NullPointerException`
    *    on a field the statement above the local assigns, at **0 scalac errors with every check
    *    count flat**.
    *
    * The two are told apart by OWNERSHIP and by nothing else (`CLAUDE.md` §4.56 — never by name,
    * never by origin line, which a real field and a promoted local can share only by accident).
    * The frontend interns a field under the CLASS and a local under the enclosing EXECUTABLE
    * (`SpoonTir.defineLocal` sets `owner = methodId`), so "is this `ValDef` a member of `owner`?"
    * is a symbol lookup. It also generalises past the funnel: any route that splices a
    * constructor's own declarations into a class body produces symbols owned by that constructor,
    * so no caller has to opt in.
    *
    * A promoted local therefore stays in `rest`, in place — the SIMPLEST faithful shape, and the
    * one that needs no `uninitialized`/assign split, because java's definite-assignment rules make
    * a forward reference from an earlier statement to a later local impossible in the first place.
    * A `val` is legal anywhere in a scala class body, so nothing about its position needs
    * repairing; only the hoist did. */
  private[emit] def orderBody(body: List[Statement], owner: SymId, paramfulPrimary: Boolean = false): List[Statement] =
    def isCtor(s: Statement) = s match { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
    // the peer ctor this one delegates to via a leading `this(args)` (NOT super, NOT the no-arg
    // primary) — its symbol identifies the exact target constructor.
    // matched THROUGH a comment wrapper (CtorFunnel.headStmt says why): a `// delegate` above the
    // `this(args)` must not turn a delegating constructor into a non-delegating one.
    def delegateTarget(d: Tree.DefDef): Option[SymId] = CtorFunnel.headStmt(d) match
      case Some(Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _))
          if sym(m).name == "<init>" && args.nonEmpty && !r.isInstanceOf[Tree.Super] => Some(m)
      case _ => None
    // a no-arg constructor whose body is only super/this delegation is degenerate — Scala's
    // implicit primary constructor already is no-arg, and `def this() = this()` self-recurses.
    // Only when the primary IS no-arg: against a PARAMFUL primary a `C() { this(16); }` — or a
    // `C() { super(0, false); }` in front of a SYNTHESISED primary — is the only thing that makes
    // `new C()` legal at all, so it must be emitted. `paramfulPrimary` therefore has to be read off
    // the emitted class, not off `Plan.primaryParams`, which a synthesised primary leaves empty.
    // …and NILARY is a question about what JAVA declared, never `paramss.flatten` — the same
    // distinction `CtorFunnel.valueParams` exists for one level up. A `C()` that gained a `(using
    // T)` clause (`DESIGN.md` §8.4) stopped being degenerate here and was emitted as
    // `def this()(using T)` beside a primary carrying the same clause: `E120` at the declaration
    // ("the same type after erasure"), and an `E051` ambiguous overload at every argument-free
    // `extends` and every `new C()`. That is CT4's third cause reappearing on the `Plan.none` side,
    // and reading value parameters restores exactly the answer this class gets with no clause at
    // all — the degenerate secondary dropped (`ENGINE-LIMITS.md` CT5).
    // …and DEGENERATE is only half of what this predicate drops. A nilary constructor whose
    // delegation CARRIES ARGUMENTS is not degenerate — java ran that delegation and scala's implicit
    // nilary primary does not — and it is dropped all the same, because there is nowhere to put it:
    // `def this()` beside a nilary primary is `E120`. That half is `CtorFunnel.Plans.droppedNilaryCtor`
    // and `OmissionCheck.droppedNilaryCtors` counts it. ONE predicate for both, so the emission and
    // the count cannot disagree about which constructors vanish.
    def dropped(d: Tree.DefDef): Boolean = !paramfulPrimary && CtorFunnel.delegationOnlyNilary(program, d).isDefined
    val ctorList = body.collect { case d: Tree.DefDef if isCtor(d) && !dropped(d) => d }
    val bySym    = ctorList.map(d => d.symbol -> d).toMap
    // DFS post-order = topological order (a target is appended before its caller); `inProgress`
    // breaks any (illegal) cycle so a malformed chain can't loop forever.
    val ordered    = collection.mutable.ListBuffer[Tree.DefDef]()
    val visited    = collection.mutable.Set[SymId]()
    val inProgress = collection.mutable.Set[SymId]()
    def visit(d: Tree.DefDef): Unit =
      if !visited(d.symbol) && !inProgress(d.symbol) then
        inProgress += d.symbol
        delegateTarget(d).flatMap(bySym.get).foreach(visit)
        inProgress -= d.symbol
        visited += d.symbol
        ordered += d
    ctorList.foreach(visit)
    // C12: a FIELD of `owner` — not merely a `ValDef`. See the doc above for why the difference is
    // ownership and for what hoisting the other kind costs.
    def isField(s: Statement) = s match { case v: Tree.ValDef => sym(v.symbol).owner == owner; case _ => false }
    // …and the OTHER kind of step-4 member: an instance (or static) INITIALISER BLOCK. JLS 12.5
    // step 4 runs field initialisers and instance initialisers as ONE sequence in TEXTUAL ORDER
    // (12.4.2 step 9 says the same of the static pair), so a block belongs in the hoisted group and
    // KEEPS ITS PLACE inside it — see the doc above.
    def isStep4(s: Statement) = s match
      case d: Tree.DefDef => isInitBlock(d)
      case _              => isField(s)
    val step4 = body.filter(isStep4)
    val rest  = body.filterNot(s => isCtor(s) || isStep4(s))
    step4 ++ ordered.toList ++ rest

