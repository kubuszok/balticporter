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
    * parameter now HAS — the frontend built these casts against the raw fill's `ResourceData[?]`,
    * and once the declaration reads `ResourceData[Object]` the same cast asserts nothing new.
    * Only wildcarded targets on an aligned symbol are touched. */

  /** a POLY EXPRESSION (JLS 15.2) — a lambda or method reference with no type of its own, taking
    * one from the slot it fills. The emitter's copy of `SpoonTir.polyExpression`'s question, asked
    * of the TIR since this is the one place a cast on such a term can still be reached (a cast the
    * java SOURCE wrote is kept by design). `uncomment`: trivia wraps a term without changing it. */
  private[emit] def polyOperand(t: Term): Boolean = Tree.uncomment(t) match
    case _: Tree.Lambda | _: Tree.MethodRef => true
    // …and a CONDITIONAL over them, which JLS 15.25 makes a poly expression too: java pushes the
    // target type through `?:`. Rendered as a CAST it fails one node out (branches elaborate to
    // `Function1`s first, then the cast asserts a `Function1` is the functional interface); as an
    // ASCRIPTION scala propagates the expected type into both arms exactly as java did.
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

  /** `this` in Scala always names the INNERMOST class, where Java's `Outer.this` names an
    * enclosing one. Qualify by simple name only when the enclosing class is actually being
    * rendered around this point. A SUPERTYPE is never qualified even when it also encloses:
    * constructor replay moves the base's `this` into the subclass body, where bare `this` is right. */
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
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    // a PROMOTED constructor body runs in the class body, so the class carries its `@nowarn`
    val promotedNowarn = if orNullClasses(cd.symbol) then nowarnDeprecated(i) else ""
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
    val (loweredBody, superArgs) = (lowerCtors(cd.body, plan), plan.superArgs)
    val pparams = plan.primaryParams
    // A SYNTHESISED primary (CtorFunnel.Plan.synthetic) has no java constructor behind it, so its
    // parameters come from the plan's own (name, type) pairs. `protected`, not `private` (scala's
    // `private` is CLASS-private, unreachable from a subclass) and not per-class-derived (a
    // whole-program question, `ENGINE-LIMITS.md` D4) — bare `protected`, matching the reference
    // ports. A DISAMBIGUATED synthesis adds a marker-typed parameter to change ARITY (`ENGINE-LIMITS.md` C8).
    val markerParam = plan.marker.map(n => s"ctor$$: ${typeValue(cd.symbol)}.${esc(n)}").toList
    // …and the CONTEXT CLAUSE a phase put on this class's constructors (`CtorFunnel.Plan.givens`),
    // rendered as its own GROUP — flattened into value parameters, `using` is lost and every
    // `summon` in the body goes unresolved (`ENGINE-LIMITS.md` CT4). Empty for a port threading
    // nothing, so no emitted byte moves.
    val givenClause = plan.givens.map(paramClause).mkString
    // A PROMOTED java constructor is still a java DECLARATION, so §8.7's mapping governs it as it
    // governs the `def this` secondaries. The SYNTHESISED primary is the exception: not a java
    // declaration, so bare `protected` is the answer — wide enough for a subclass in another
    // package to extend it, narrow enough that nothing else legitimately calls it.
    val ctorVis = plan.primary.map(pc => vis(sym(pc.symbol), privateQualifier(s.owner))).getOrElse("")
    // …and a promoted parameter the java constructor ASSIGNS TO is a `var`. A java ctor parameter
    // is an ordinary LOCAL and may be reassigned (`x = x*2` promotes to `E052 Reassignment to val`
    // otherwise) — a record's compact constructor makes this ordinary (JLS 8.10.4). `private var`,
    // never a bare `var`: java's parameter is not a member, so no name reaches the emitted surface.
    // Decided by SYMBOL over the LOWERED body (§4.56) — every write here is a `Tree.Assign`.
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
      // a class whose constructor java declared NILARY and the pipeline gave a clause: the clause
      // is the whole parameter list, `class C(using T)`. A NILARY constructor that is not public
      // still needs the modifier's place — with a context clause it already exists and must NOT
      // gain an empty group before it (`()(using Ctx)` is a different signature).
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
    // T22 — an `@interface`'s ELEMENTS (JLS 9.6.1) become the emitted class's CONSTRUCTOR
    // PARAMETERS, so they are taken out of the body BEFORE `memberStat` runs — rendered as members
    // they land in a `body` the annotation arm below discards, leaving planned-and-never-written
    // slots (`!! UNLOCATABLE`, and a relocated javadoc — §4.58's recovery lane, not two defects).
    val (annotElems, instance) =
      if !s.flags.isAnnotation then (Nil, instance0)
      else instance0.partition {
        case d: Tree.DefDef => sym(d.symbol).name != "<init>" && d.paramss.forall(_.isEmpty)
        case _              => false
      }
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body1   = joinStats(classBodyStats(orderBody(instance, cd.symbol, paramfulPrimary), plan, i + 1).filter(_.nonEmpty))
    // K22 — the CLASS-INITIALISATION trigger, ahead of every other statement since that is where
    // java ran it (`statics` carries both `static { }` blocks and static field initialisers). Only
    // where there is a CONSTRUCTOR to hang it on: a `trait` body statement runs at every
    // implementor's init (more than java does), and an interface may not declare one (JLS 9.1.1) —
    // and never where forcing would RE-ENTER an in-progress initialisation (`ENGINE-LIMITS.md` K22 face 2).
    val force   = if hasClinit(statics) && kw == "class" && !s.flags.isAnnotation &&
                     !reentrantBearers.contains(cd.symbol)
                  then forceCompanion(cd, cd.symbol, balticporter.tir.ClassInitTriggerCheck.Instantiation, i + 1)
                  else ""
    // C3 item 4 — parent secondary's post-body, guarded. // ENGINE-LIMITS C3
    val postBody = currentClass.map(plans.primaryPostBodyFor(_)).getOrElse(Nil)
    val postBodyStr =
      if postBody.isEmpty || plan.postBodySlots.isEmpty then ""
      else
        val guardParam = plan.postBodySlots.head._1
        val isBool = guardParam == "via$pb"
        val cond = if isBool then guardParam else s"$guardParam != null"
        val pbStats = postBody.map(stat(_, i + 2)).filter(_.trim.nonEmpty)
        if pbStats.isEmpty then ""
        else s"${ind(i + 1)}if ($cond) {\n${pbStats.mkString("\n")}\n${ind(i + 1)}}"
    // JS-C43 — the members javac derives from a record header, which no java declaration carries.
    val (recMembers, recStatics, recNote) = recordMembers(cd, s, i)
    val body0   = joinStats(List(bnote, force, postBodyStr, body1, recMembers.mkString("\n")).filter(_.nonEmpty))
    val diamonds = diamondOverrides(cd, i + 1)
    val body    = if diamonds.isEmpty then body0 else joinStats(List(body0).filter(_.nonEmpty) ++ diamonds)
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    // §8.7 renders every nested type QUALIFIED (`private[TopLevel]`/`private[pkg]`), so the
    // class-header erasure this rule once needed is gone. A `@interface` emitted as a trait cannot
    // be applied as an annotation (161 corpus errors) — the equivalent is `extends StaticAnnotation`.
    // A PROMOTED constructor's Javadoc joins the CLASS's own (`CtorFunnel` removed its `def`) —
    // dropping it cost 138 Javadoc losses on libGDX core (`TriviaCheck`, the largest category).
    val ctorLead = plan.primary.toList.flatMap(_.leading)
    // …and its NOTES go the same way (§4.575): a PROMOTED constructor has no `def` for an
    // `AtDeclaration` note to sit above, so a decision subjected at it produced NO NOTE at all
    // (measured 1 on libGDX base, `ENGINE-LIMITS.md` I9). The class is where scala documents a
    // primary constructor, matching the javadoc above.
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
      else s"${leading(cd.leading ++ ctorLead, i)}$cnote$ctorNote$sealNote$recNote${annots(s, i)}$promotedNowarn${ind(i)}${mods(s, privateQualifier(s.owner))}$seal$abs$kw ${esc(s.name)}$tps$prim$ext$open"
    // Java interface/parent CONSTANTS are `static`, living in the parent's companion, which Scala
    // does NOT inherit — re-export each parent's companion so an inherited constant resolves,
    // excluding the class's OWN static names (a subtype may redeclare one). A STATIC INITIALIZER
    // BLOCK (`<clinit>`) has no Scala identifier to spell and lowers into the companion's body, so
    // it is never in the set. Needs BOTH a statics-bearing parent AND an own `static{}` at once.
    val ownStaticNames = statics.collect {
      case d: Definition if !d.isInstanceOf[Tree.DefDef] || !isInitBlock(d.asInstanceOf[Tree.DefDef]) =>
        esc(sym(d.symbol).name)
    }.distinct
    // Two exports must not both deliver the same name: `GLInterceptor` already re-exports `GL20`'s
    // constants via `extends GLInterceptor with GL20`, so a second `export GL20.*` duplicates.
    // Drop a subsumed parent; for a DIAMOND, exclude from each later export every name an earlier
    // one delivered FROM THE SAME DECLARING TYPE (a genuine redeclaration has a different owner).
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
      // K22's SECOND trigger — JLS 12.4.1 item 7: initialising a class initialises its SUPERCLASS
      // first, which a bare `S.member` read cannot reach in Scala (touches only `object S`).
      // Forced FIRST in the companion body (java ran the ancestor's initialiser first). Asks
      // `hasCompanion` rather than re-deriving "does anything read a static of this type" (§4.56).
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

  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a companion
    * `object` holding each constant as a `case object` and a `values` array. */

  /** A java ENUM takes ONE of two shapes, a fact about java's own declaration — [[balticporter.tir.EnumShape]],
    * read here and by `OmissionCheck.enumShapeRefusals` so the two can never disagree. Scala 3
    * `enum` IS a `java.lang.Enum[X]`, answering every bound/`EnumSet`/`EnumMap` a ported enum
    * needs. A class BODY on a constant, or a member colliding with `java.lang.Enum`'s finals, keeps
    * the sealed shape instead — a REFUSAL, counted, never silently chosen. */
  private[emit] def enumDef(cd: Tree.ClassDef, i: Int): String =
    if balticporter.tir.EnumShape.isScalaEnum(program, cd) then scalaEnumDef(cd, i) else sealedEnumDef(cd, i)

  /** The parts BOTH enum shapes are made of, derived ONCE. The two arms differ only in the header
    * and in what `java.lang.Enum` supplies — never in the primary constructor, its promoted
    * params, superseded fields or surviving statements. Two derivations would drift (§4.56). */
  private[emit] final case class EnumParts(ctorParams: List[Tree.ValDef], paramNames: Set[String],
                                     instance: List[Statement], ctorStats: List[Statement],
                                     statics: List[Statement], eprimary: String)

  private[emit] def enumParts(cd: Tree.ClassDef): EnumParts =
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the primary constructor's params (as `var` fields).
    // JAVA's parameters, never `paramss.flatten` — a context clause is ANONYMOUS and cannot become
    // a named field, so it is dropped and COUNTED (`ENGINE-LIMITS.md` CT5). THE ROOT, never
    // `ctors.head`: taking the head's params silently gave a delegating overload an EMPTY primary
    // (`CtorFunnel.enumPrimaryCtor`, `OmissionCheck.overloadedEnumCtors` counts refusals).
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

  /** THE PRE-EXISTING SHAPE — a `sealed abstract class` plus one `case object` per constant. What
    * a java enum the scala 3 `enum` cannot express is emitted as; does NOT extend
    * `java.lang.Enum[X]`, so `name()`/`ordinal()`/`values()`/`valueOf` are all supplied by hand.
    * `EnumShape.refusal` decides which enums arrive here; `OmissionCheck` counts them. */
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
    * class body it becomes `E091`. Wrapped in a local `def` (a valid `return` target, unlike
    * `boundary.Break`) inside a block so it is not a class member. Only `plan.primaryBody` moves
    * inside; [[returnsIn]] stops at a nested `Lambda`/`DefDef`/`AnonClass`. A value-carrying
    * `return` is refused (javac itself rejects it in a constructor). */
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

  // K22 — a java class initialiser (JLS 12.4.2 step 9) runs at class init; a scala companion
  // initialises only when something touches the OBJECT. Reproduced only at java's own trigger
  // list (JLS 12.4.1): INSTANTIATION (forced ahead of every field initialiser), STATIC ACCESS
  // (needs nothing), SUBCLASS INITIALISATION (item 7, force the nearest bearing ancestor).
  // REFLECTION cannot be reproduced and is stated, not counted (`ENGINE-LIMITS.md` K22).

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

  /** Scala secondaries must delegate to a PRECEDING constructor, so order fields first, then
    * constructors in DELEGATION-TOPOLOGICAL order (`this(...)` edges, not arity), then everything
    * else. `owner` decides WHICH `ValDef`s hoist (`ENGINE-LIMITS.md` C12): java's STEP-4 members
    * hoist ahead of the body (field initialisers AND instance init blocks, textual order); a
    * PROMOTED CONSTRUCTOR LOCAL stays in place. Told apart by OWNERSHIP (§4.56), never by name. */
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
    // implicit primary is already no-arg. Only when the primary IS no-arg (`paramfulPrimary` read
    // off the EMITTED class). NILARY is about what JAVA declared, never `paramss.flatten`.
    // DEGENERATE is only half: a nilary delegation CARRYING ARGUMENTS is also dropped (`E120`) —
    // `CtorFunnel.Plans.droppedNilaryCtor`, ONE predicate for both emission and count.
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

