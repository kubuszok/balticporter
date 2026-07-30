package balticporter.frontend.spoon

// =============================================================================
// FROZEN — the BIR frontend. New work goes on `SpoonTir`, beside this file.
//
// This produces `BUnit`s: the untyped Java IR described (and frozen) at the head of
// `core/Bir.scala`. `SpoonTir` produces a `Program` — symbol table, cross-reference
// index, phase pipeline — and that is where every rule since has been written.
//
// Kept because liqp, xwiki/flexmark and jbump still port through it (the corpus
// programs are listed in `core/Bir.scala`). Fix what they need; add nothing.
// =============================================================================

import balticporter.core.*
import balticporter.core.BExpr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Frontend on Spoon 11.x (ECJ underneath, full-classpath mode, comments enabled).
  * The only module that sees Spoon types (DESIGN.md §3.2 insulation rule).
  */
final class SpoonFrontend extends Frontend:

  def parse(cfg: FrontendConfig): List[BUnit] =
    val typesByFile = buildModel(cfg)
    cfg.files.map { rel =>
      val abs = cfg.sourceRoot.resolve(rel).toRealPath()
      val types = typesByFile.getOrElse(abs, throw Unsupported(rel, "-", "file produced no types"))
      val src = Files.readString(abs)
      new UnitBuilder(rel, src).build(types)
    }

  /** Like parse, but isolates conversion failures per file (corpus/coverage runs).
    * The model is still built once over all files.
    */
  def parseTolerant(cfg: FrontendConfig): List[(String, Either[Throwable, BUnit])] =
    val typesByFile = buildModel(cfg)
    cfg.files.map { rel =>
      rel -> scala.util.Try {
        val abs = cfg.sourceRoot.resolve(rel).toRealPath()
        val types = typesByFile.getOrElse(abs, throw Unsupported(rel, "-", "file produced no types"))
        val src = Files.readString(abs)
        new UnitBuilder(rel, src).build(types)
      }.toEither
    }

  private def buildModel(cfg: FrontendConfig): Map[Path, List[CtType[?]]] =
    val launcher = new Launcher
    val env = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setCommentEnabled(true)
    env.setNoClasspath(false)
    env.setSourceClasspath(cfg.classpath.map(_.toString).toArray)
    if cfg.resolutionRoots.nonEmpty then
      // whole roots participate in resolution; conversion is limited to cfg.files
      cfg.resolutionRoots.foreach(r => launcher.addInputResource(r.toString))
      val covered = cfg.resolutionRoots.map(_.toRealPath())
      cfg.files
        .map(f => cfg.sourceRoot.resolve(f).toRealPath())
        .filterNot(abs => covered.exists(abs.startsWith))
        .foreach(abs => launcher.addInputResource(abs.toString))
    else cfg.files.foreach(f => launcher.addInputResource(cfg.sourceRoot.resolve(f).toString))
    val model = launcher.buildModel()

    model.getAllTypes.asScala.toList
      .filter(t => t.getPosition != null && t.getPosition.isValidPosition)
      .groupBy(t => t.getPosition.getFile.toPath.toRealPath())
      .view
      .mapValues(_.sortBy(t => t.getPosition.getSourceStart))
      .toMap

private final class UnitBuilder(sourcePath: String, source: String):

  private def unsupported(el: CtElement, what: String): Nothing =
    val pos = el.getPosition
    val line = if pos != null && pos.isValidPosition then pos.getLine.toString else "?"
    throw Unsupported(sourcePath, line, what)

  // ---- trivia ----------------------------------------------------------------

  /** Verbatim comment text sliced from the original source (delimiters included). */
  private def triviaOf(c: CtComment): Trivia =
    val kind = c.getCommentType match
      case CtComment.CommentType.JAVADOC => TriviaKind.Javadoc
      case CtComment.CommentType.INLINE  => TriviaKind.Line
      case _                             => TriviaKind.Block
    val pos = c.getPosition
    val text =
      if pos != null && pos.isValidPosition && pos.getSourceEnd >= pos.getSourceStart then
        source.substring(pos.getSourceStart, pos.getSourceEnd + 1)
      else c.toString
    Trivia(kind, text)

  /** every comment handed out as trivia, by identity — deepComments only scoops
    * what no closer harvest point claimed. */
  private val claimed: java.util.Set[CtComment] = java.util.Collections.newSetFromMap(
    new java.util.IdentityHashMap[CtComment, java.lang.Boolean]())

  private def leadingOf(el: CtElement): List[Trivia] =
    el.getComments.asScala.toList.map { c => claimed.add(c); triviaOf(c) }

  /** comments Spoon attached to expression-level descendants (arg lists, fluent
    * chains, initializer exprs). BIR carries no expression trivia, so they hoist
    * to the nearest enclosing harvest point. Call AFTER translating children so
    * nested statements claim their own first. */
  private def deepComments(el: CtElement): List[Trivia] =
    el.getElements(new spoon.reflect.visitor.filter.TypeFilter[CtComment](classOf[CtComment]))
      .asScala.toList
      .filter(claimed.add)
      .map(triviaOf)

  // ---- types -----------------------------------------------------------------

  private def btype(tr: CtTypeReference[?]): BType = tr match
    case null => BType.Ref(BType.ObjectQ, Nil)
    case arr: CtArrayTypeReference[?] => BType.Arr(btype(arr.getComponentType))
    case tv: CtTypeParameterReference =>
      tv match
        case w: CtWildcardReference =>
          val bound = Option(w.getBoundingType).map(btype)
          if w.isUpper then BType.Wild(bound, None) else BType.Wild(None, bound)
        case _ => BType.TVar(tv.getSimpleName)
    case p if p.isPrimitive => BType.Prim(p.getSimpleName)
    case r =>
      val args = r.getActualTypeArguments.asScala.toList.map(btype)
      // Java raw types are illegal in Scala type positions — fill each slot. A type
      // parameter with a NON-Object, non-self-referential upper bound (F-bounded
      // handlers: <N extends Node>) fills with that bound so the raw type stays
      // assignment-compatible with the bound-parameterized form the API expects;
      // plain Object-bounded params (Map<K,V>) fill with `?` as before.
      val filled =
        if args.nonEmpty then args
        else rawFill(r)
      BType.Ref(r.getQualifiedName, filled)

  private val arityCache = collection.mutable.Map[String, Int]()

  /** formal type-parameter count of the referenced declaration (0 when unresolvable). */
  private def rawArity(r: CtTypeReference[?]): Int =
    arityCache.getOrElseUpdate(
      r.getQualifiedName,
      scala.util.Try(Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.size).getOrElse(0)).getOrElse(0),
    )

  /** Fill args for a raw type: the type param's non-Object simple upper bound where
    * there is one, else `?`. Self-referential F-bounds (T extends IRichSequence<T>)
    * and type-var-carrying bounds fall back to `?` to avoid unbound references. */
  private def rawFill(r: CtTypeReference[?]): List[BType] =
    val wild = BType.Wild(None, None)
    scala.util.Try {
      val tps = r.getTypeDeclaration.getFormalCtTypeParameters.asScala.toList
      tps.map { tp =>
        Option(tp.getSuperclass).map(btype) match
          case Some(b: BType.Ref) if b.qname != BType.ObjectQ && !hasTypeVar(b) => b
          case _                                                                => wild
      }
    }.toOption.filter(_.length == rawArity(r)).getOrElse(List.fill(rawArity(r))(wild))

  private def hasTypeVar(t: BType): Boolean = t match
    case _: BType.TVar    => true
    case BType.Ref(_, as) => as.exists(hasTypeVar)
    case BType.Arr(e)     => hasTypeVar(e)
    case BType.Wild(u, l) => u.exists(hasTypeVar) || l.exists(hasTypeVar)
    case _                => false

  // ---- declarations ----------------------------------------------------------

  def build(types: List[CtType[?]]): BUnit =
    val pkg = types.headOption.flatMap(t => Option(t.getPackage)).map(_.getQualifiedName).getOrElse("")
    // file-header comments live on the compilation unit (before `package`) or on
    // the import declarations (between `package` and the first import)
    val header = types.headOption
      .map { t =>
        val cu = t.getPosition.getCompilationUnit
        val cuc = cu.getComments.asScala.toList
        val imp = cu.getImports.asScala.toList.flatMap(_.getComments.asScala)
        (cuc ++ imp).filter(claimed.add).map(triviaOf)
      }
      .getOrElse(Nil)
    val decls = types.map(typeDecl) match
      case first :: rest if header.nonEmpty => first.copy(leading = header ++ first.leading) :: rest
      case l                                => l
    BUnit(sourcePath, pkg, decls, CommentScanner.scan(source))

  private def mods(m: CtModifiable, isOverride: Boolean = false): Mods =
    val vis =
      if m.hasModifier(ModifierKind.PUBLIC) then Vis.Public
      else if m.hasModifier(ModifierKind.PROTECTED) then Vis.Protected
      else if m.hasModifier(ModifierKind.PRIVATE) then Vis.Private
      else Vis.PackagePrivate
    val anns = preservedAnnotations(m.asInstanceOf[CtElement & CtModifiable])
    Mods(
      vis = vis,
      isAbstract = m.hasModifier(ModifierKind.ABSTRACT),
      isFinal = m.hasModifier(ModifierKind.FINAL),
      isStatic = m.hasModifier(ModifierKind.STATIC),
      isOverride = isOverride,
      annotations = anns,
    )

  private val ignoredAnnotations = Set(
    "java.lang.Override", "java.lang.SuppressWarnings", "java.lang.SafeVarargs",
    "java.lang.FunctionalInterface", // Scala SAM conversion needs no marker
    "java.lang.Deprecated",          // mapped to scala.deprecated in preservedAnnotations
  )

  /** annotations carried through to the output verbatim. Jackson annotations are
    * behavior-bearing on the JVM (custom serializers drive the eager-render path) —
    * a cross-platform port would substitute them (ssg's disposition), but the
    * JVM-faithful port preserves them. */
  private val preservedAnnotationPrefixes = List("org.junit.", "junit.", "com.fasterxml.jackson.")

  private def checkAnnotations(el: CtElement & CtModifiable): Boolean =
    var hasOverride = false
    el.getAnnotations.asScala.foreach { a =>
      val q = a.getAnnotationType.getQualifiedName
      if q == "java.lang.Override" then hasOverride = true
      // JetBrains nullness annotations: advisory only — dropped (the Nullable idiom is
      // Tier-3 work driven by real null-flow analysis, not these hints)
      else if !ignoredAnnotations.contains(q) && !preservedAnnotationPrefixes.exists(q.startsWith)
        && !q.startsWith("org.jetbrains.annotations.") && !q.startsWith("org.intellij.lang.annotations.")
      then unsupported(el, s"annotation @$q")
    }
    hasOverride

  private def preservedAnnotations(el: CtElement & CtModifiable): List[BAnnotation] =
    el.getAnnotations.asScala.toList
      .filter(a =>
        preservedAnnotationPrefixes.exists(a.getAnnotationType.getQualifiedName.startsWith)
          || a.getAnnotationType.getQualifiedName == "java.lang.Deprecated")
      .map { a =>
        val q = a.getAnnotationType.getQualifiedName
        if q == "java.lang.Deprecated" then BAnnotation("scala.deprecated", Nil)
        else
          val args = a.getValues.asScala.toList.map { (k, v) =>
            k -> (v match
              case e: CtExpression[?] => expr(e)
              case other              => unsupported(a, s"annotation value $other"))
          }
          BAnnotation(q, args)
      }

  private def typeDecl(t: CtType[?]): BTypeDecl = t match
    // NOTE: CtEnum extends CtClass — must match first
    case e: CtEnum[?] =>
      checkAnnotations(e)
      val nestedE = nestedOf(e)
      val (svuid, fields, staticFields) = extractFields(e)
      val (staticM, instanceM) =
        e.getMethods.asScala.toList.filterNot(_.isImplicit).sortBy(posKey).partition(_.hasModifier(ModifierKind.STATIC))
      val cases = e.getEnumValues.asScala.toList.map { v =>
        val args = v.getDefaultExpression match
          case null => Nil
          case cc: CtConstructorCall[?] =>
            if cc.isInstanceOf[CtNewClass[?]] then unsupported(v, "enum constant with class body")
            cc.getArguments.asScala.toList.map(expr)
          case other => unsupported(v, s"enum constant initializer ${other.getClass.getSimpleName}")
        BEnumCase(leadingOf(v), v.getSimpleName, args)
      }
      BTypeDecl(
        leading = leadingOf(e),
        mods = mods(e),
        kind = BTypeKind.Enum,
        name = e.getSimpleName,
        tparams = Nil,
        superClass = None,
        interfaces = e.getSuperInterfaces.asScala.toList.map(btype),
        fields = fields,
        ctors = e.getConstructors.asScala.toList.filterNot(_.isImplicit).map(ctorDecl),
        methods = instanceM.map(methodDecl),
        staticFields = staticFields,
        staticMethods = staticM.map(methodDecl),
        enumCases = cases,
        nested = nestedE._1,
        inner = nestedE._2,
        serialVersionUID = svuid,
      )

    case c: CtClass[?] =>
      checkAnnotations(c)
      val nestedC = nestedOf(c)
      val (svuid, fields, staticFields) = extractFields(c)
      val (staticM, instanceM) = c.getMethods.asScala.toList.sortBy(posKey).partition(_.hasModifier(ModifierKind.STATIC))
      val (staticBlocks, instanceBlocks) = c.getAnonymousExecutables.asScala.toList
        .sortBy(posKey)
        .partition(_.hasModifier(ModifierKind.STATIC))
      def initStmts(blocks: List[CtAnonymousExecutable]): List[BStmt] =
        blocks.flatMap { b =>
          BStmt(leadingOf(b), BStmtK.Empty) :: block(b.getBody.getStatements.asScala.toList)
        }
      BTypeDecl(
        leading = leadingOf(c),
        mods = mods(c),
        kind = BTypeKind.Class,
        name = c.getSimpleName,
        tparams = tparamsOf(c),
        superClass = Option(c.getSuperclass).filter(_.getQualifiedName != BType.ObjectQ).map { s =>
          btype(s) match
            case r: BType.Ref => r
            case other        => unsupported(c, s"superclass type $other")
        },
        interfaces = c.getSuperInterfaces.asScala.toList.map(btype),
        fields = fields,
        ctors = c.getConstructors.asScala.toList.filterNot(_.isImplicit).map(ctorDecl),
        methods = instanceM.map(methodDecl),
        staticFields = staticFields,
        staticMethods = staticM.map(methodDecl),
        staticInit = initStmts(staticBlocks),
        instanceInit = initStmts(instanceBlocks),
        nested = nestedC._1,
        inner = nestedC._2,
        serialVersionUID = svuid,
      )
    case i: CtInterface[?] =>
      checkAnnotations(i)
      val nestedI = nestedOf(i)
      // Java interface fields are implicitly public static final → companion object
      val (svuid, _, staticFields) = extractFields(i)
      val (staticM, instanceM) = i.getMethods.asScala.toList.sortBy(posKey).partition(_.hasModifier(ModifierKind.STATIC))
      BTypeDecl(
        leading = leadingOf(i),
        mods = mods(i),
        kind = BTypeKind.Interface,
        name = i.getSimpleName,
        tparams = tparamsOf(i),
        superClass = None,
        interfaces = i.getSuperInterfaces.asScala.toList.map(btype),
        fields = Nil,
        ctors = Nil,
        methods = instanceM.map(methodDecl),
        staticFields = staticFields,
        staticMethods = staticM.map(methodDecl),
        nested = nestedI._1,
        inner = nestedI._2,
        serialVersionUID = svuid,
      )
    case other => unsupported(other, s"type kind ${other.getClass.getSimpleName}")

  /** Static nested types → companion members; inner (non-static) classes → class
    * body (Scala nested classes are inner by default, capturing the outer instance).
    */
  private def nestedOf(t: CtType[?]): (List[BTypeDecl], List[BTypeDecl]) =
    val (stat, inn) = t.getNestedTypes.asScala.toList.sortBy(posKey).partition { n =>
      n.hasModifier(ModifierKind.STATIC) ||
      n.isInstanceOf[CtInterface[?]] || n.isInstanceOf[CtEnum[?]] || t.isInstanceOf[CtInterface[?]]
    }
    (stat.map(typeDecl), inn.map(typeDecl))

  private def tparamsOf(t: CtFormalTypeDeclarer): List[BTypeParam] =
    t.getFormalCtTypeParameters.asScala.toList.map { tp =>
      BTypeParam(tp.getSimpleName, Option(tp.getSuperclass).map(btype))
    }

  private def posKey(el: CtElement): Int =
    val p = el.getPosition
    if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue

  /** returns (serialVersionUID, instance fields, static fields). Interface fields are
    * implicitly static.
    */
  private def extractFields(c: CtType[?]): (Option[Long], List[BField], List[BField]) =
    var svuid: Option[Long] = None
    val instance = List.newBuilder[BField]
    val statics = List.newBuilder[BField]
    val implicitlyStatic = c.isInstanceOf[CtInterface[?]]
    // enum constants surface as fields too — they're handled via getEnumValues
    c.getFields.asScala.toList.filterNot(_.isInstanceOf[CtEnumValue[?]]).foreach { f =>
      checkAnnotations(f)
      val isStatic = f.hasModifier(ModifierKind.STATIC) || implicitlyStatic
      if f.getSimpleName == "serialVersionUID" && isStatic then
        svuid = Option(f.getDefaultExpression).collect { case l: CtLiteral[?] =>
          l.getValue match
            case n: java.lang.Number => n.longValue
            case v                   => unsupported(f, s"serialVersionUID value $v")
        }
      else
        val fLeading = leadingOf(f)
        val fInit = Option(f.getDefaultExpression).map(expr)
        val bf = BField(
          leading = fLeading ++ deepComments(f),
          mods = mods(f),
          tpe = btype(f.getType),
          name = f.getSimpleName,
          init = fInit,
        )
        if isStatic then statics += bf else instance += bf
    }
    (svuid, instance.result(), statics.result())

  private def paramsOf(e: CtExecutable[?]): List[BParam] =
    e.getParameters.asScala.toList.map { p =>
      BParam(p.getSimpleName, btype(p.getType), p.isVarArgs)
    }

  private def ctorDecl(c: CtConstructor[?]): BCtor =
    checkAnnotations(c)
    val stmts = Option(c.getBody).map(_.getStatements.asScala.toList).getOrElse(Nil)
    var invTrivia = List.empty[Trivia]
    var targetTypes = Option.empty[List[BType]]
    val (superArgs, thisArgs, rest) = stmts match
      case (inv: CtInvocation[?]) :: tail if inv.getExecutable != null && inv.getExecutable.isConstructor =>
        val ownerQ = Option(inv.getExecutable.getDeclaringType).map(_.getQualifiedName)
        val isThisCall = ownerQ.contains(c.getDeclaringType.getQualifiedName)
        val args = inv.getArguments.asScala.toList.map(expr)
        // the resolved target ctor's formal param types — disambiguates same-arity
        // overloads when the effect-replay funnel inlines this super/this call
        targetTypes = scala.util.Try(
          inv.getExecutable.getParameters.asScala.toList.map(btype)
        ).toOption
        // the invocation statement itself is consumed — its comments hoist to the ctor
        invTrivia = leadingOf(inv) ++ deepComments(inv)
        if isThisCall then (None, Some(args), tail) else (Some(args), None, tail)
      case _ => (None, None, stmts)
    BCtor(
      leading = leadingOf(c) ++ invTrivia,
      mods = mods(c),
      params = paramsOf(c),
      superArgs = superArgs,
      thisArgs = thisArgs,
      body = block(rest),
      callTargetTypes = targetTypes,
    )

  private def methodDecl(m: CtMethod[?]): BMethod =
    val isOverride = checkAnnotations(m)
    val assigned = collection.mutable.Set[String]()
    Option(m.getBody).foreach { b =>
      m.getParameters.asScala.foreach { p =>
        if writesToVar(b, p.getSimpleName) then assigned += p.getSimpleName
      }
    }
    val mLeading = leadingOf(m)
    BMethod(
      leading = mLeading,
      mods = mods(m, isOverride),
      tparams = tparamsOf(m),
      name = m.getSimpleName,
      params = paramsOf(m),
      ret = btype(m.getType),
      body = Option(m.getBody).map(b => block(b.getStatements.asScala.toList)),
      assignedParams = assigned.toSet,
    )

  // ---- statements ------------------------------------------------------------

  private def block(stmts: List[CtStatement]): List[BStmt] =
    val out = List.newBuilder[BStmt]
    var pending = List.empty[Trivia]
    stmts.foreach {
      case c: CtComment => pending = pending :+ triviaOf(c)
      case s =>
        val own = leadingOf(s)
        val k = stmt(s)
        out += BStmt(pending ++ own ++ deepComments(s), k)
        pending = Nil
    }
    if pending.nonEmpty then out += BStmt(pending, BStmtK.Empty)
    out.result()

  private def blockOf(s: CtStatement): List[BStmt] = s match
    case null => Nil
    case b: CtBlock[?] =>
      // the block's OWN comments (e.g. `} else /* note */ {`) become leading trivia
      val own = leadingOf(b)
      val inner = block(b.getStatements.asScala.toList)
      if own.isEmpty then inner else BStmt(own, BStmtK.Empty) :: inner
    case single =>
      val own = leadingOf(single)
      val k = stmt(single)
      List(BStmt(own ++ deepComments(single), k))

  private def stmt(s: CtStatement): BStmtK = s match
    case v: CtLocalVariable[?] =>
      val reassigned = enclosingBodyHasWriteTo(v)
      val init = Option(v.getDefaultExpression).map(maybeUncheckedCast(v.getType, _))
      BStmtK.LocalVar(v.getSimpleName, btype(v.getType), init, !reassigned)
    case a: CtOperatorAssignment[?, ?] =>
      val op = binOp(a.getKind, a)
      val lhsNarrow = Option(a.getAssigned.getType).map(_.getSimpleName).exists(Set("char", "byte", "short"))
      // Java compound assignment auto-narrows the widened result back to the target's
      // narrow type; Scala's `c |= x` desugars to `c = (c | x): Int` and fails to fit
      // Char/Byte/Short. Emit `c = (c op x).toChar` explicitly (Cast of a Prim renders
      // as `.toChar`/`.toByte`/`.toShort`).
      if lhsNarrow then
        val lhs = expr(a.getAssigned)
        BStmtK.Assign(lhs, Cast(btype(a.getAssigned.getType), Binary(op, lhs, expr(a.getAssignment))), None)
      else BStmtK.Assign(expr(a.getAssigned), expr(a.getAssignment), Some(op))
    case a: CtAssignment[?, ?] =>
      BStmtK.Assign(expr(a.getAssigned), maybeUncheckedCast(a.getAssigned.getType, a.getAssignment), None)
    case i: CtIf =>
      BStmtK.If(expr(i.getCondition), blockOf(i.getThenStatement), Option(i.getElseStatement).map(blockOf))
    case r: CtReturn[?] =>
      // returning into a method-generic T: javac unchecked-converts; Scala needs the
      // cast to the (in-scope) type variable
      val enclosing = Option(r.getParent(classOf[CtMethod[?]]))
      val retTVar = enclosing
        .map(_.getType)
        .collect { case tp: CtTypeParameterReference => tp.getSimpleName }
        .filter(n => enclosing.exists(_.getFormalCtTypeParameters.asScala.exists(_.getSimpleName == n)))
      BStmtK.Return(Option(r.getReturnedExpression).map { e =>
        val base = expr(e)
        retTVar match
          case Some(n) if !Option(e.getType).exists(_.isInstanceOf[CtTypeParameterReference]) =>
            Cast(BType.TVar(n), base)
          case _ => base
      })
    case t: CtThrow =>
      BStmtK.Throw(expr(t.getThrownExpression))
    case w: CtWhile =>
      val (hasB, hasC) = analyzeLoop(w)
      val body = maybeContinueBoundary(hasC, blockOf(w.getBody))
      maybeBreakBoundary(hasB, BStmtK.While(expr(w.getLoopingExpression), body))

    case b: CtBreak =>
      if b.getTargetLabel != null then unsupported(b, "labeled break")
      val owner = nearestOwner(b, includeSwitch = true)
      if owner != null && breakLoops.contains(owner) then
        BStmtK.LoopBreak(label = if mixedLoops.contains(owner) then Some("break$") else None)
      else
        val pos = if b.getPosition.isValidPosition then s" (line ${b.getPosition.getLine})" else ""
        unsupported(b, s"break not owned by a translated loop$pos")

    case c: CtContinue =>
      if c.getTargetLabel != null then unsupported(c, "labeled continue")
      val owner = nearestOwner(c, includeSwitch = false)
      if owner != null && continueLoops.contains(owner) then BStmtK.LoopBreak()
      else unsupported(c, "continue not owned by a translated loop")
    case b: CtBlock[?] =>
      BStmtK.Block(block(b.getStatements.asScala.toList))
    case i: CtInvocation[?] =>
      BStmtK.ExprStmt(expr(i))

    case c: CtClass[?] => // Java local class (Spoon prefixes the name with a block counter)
      val t = typeDecl(c)
      BStmtK.LocalType(t.copy(name = t.name.dropWhile(_.isDigit)))

    case f: CtForEach => forEach(f)
    case f: CtFor     => classicFor(f)

    case a: CtAssert[?] =>
      BStmtK.Assert(expr(a.getAssertExpression), Option(a.getExpression).map(expr))

    case d: CtDo =>
      val (hasB, hasC) = analyzeLoop(d)
      val body = maybeContinueBoundary(hasC, blockOf(d.getBody))
      maybeBreakBoundary(hasB, BStmtK.DoWhile(body, expr(d.getLoopingExpression)))

    case t: CtTryWithResource => unsupported(t, "try-with-resources (not yet supported)")
    case t: CtTry =>
      val catches = t.getCatchers.asScala.toList.map { c =>
        val p = c.getParameter
        val types =
          if p.getMultiTypes.asScala.nonEmpty then p.getMultiTypes.asScala.toList.map(btype)
          else List(btype(p.getType))
        BCatch(p.getSimpleName, types, blockOf(c.getBody))
      }
      BStmtK.Try(blockOf(t.getBody), catches, Option(t.getFinalizer).map(blockOf))

    case sy: CtSynchronized =>
      BStmtK.Synchronized(expr(sy.getExpression), blockOf(sy.getBlock))

    case s: CtSwitch[?] => switchToMatch(s)

    // i++ / i-- / ++i / --i in statement position → i += 1 / i -= 1
    case u: CtUnaryOperator[?] =>
      import UnaryOperatorKind.*
      u.getKind match
        case POSTINC | PREINC => BStmtK.Assign(expr(u.getOperand), Lit(LitKind.IntL, "1"), Some("+"))
        case POSTDEC | PREDEC => BStmtK.Assign(expr(u.getOperand), Lit(LitKind.IntL, "1"), Some("-"))
        case _                => BStmtK.ExprStmt(expr(u))

    case other => unsupported(other, s"statement ${other.getClass.getSimpleName}")

  // ---- break/continue → scala.util.boundary --------------------------------

  private val breakLoops = collection.mutable.Set[CtElement]()
  private val continueLoops = collection.mutable.Set[CtElement]()

  /** nearest enclosing construct an unlabeled break (incl. switches) / continue targets. */
  private def nearestOwner(s: CtElement, includeSwitch: Boolean): CtElement =
    var p = s.getParent
    var found: CtElement = null
    while p != null && found == null do
      p match
        case _: CtLoop                          => found = p
        case _: CtSwitch[?] if includeSwitch    => found = p
        case _                                  => ()
      if found == null then p = p.getParent
    found

  /** Registers the loop's break/continue mode. Mixed break+continue nests two
    * boundaries: the outer (break target) names its Label `break$`, so translated
    * breaks use `break()(using break$)` while continues hit the inner unnamed one.
    */
  private def analyzeLoop(loop: CtElement): (Boolean, Boolean) =
    val hasB = loop
      .getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtBreak]))
      .asScala
      .exists(b => nearestOwner(b, includeSwitch = true) eq loop)
    val hasC = loop
      .getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtContinue]))
      .asScala
      .exists(c => nearestOwner(c, includeSwitch = false) eq loop)
    if hasB then breakLoops += loop
    if hasC then continueLoops += loop
    if hasB && hasC then mixedLoops += loop
    (hasB, hasC)

  private val mixedLoops = collection.mutable.Set[CtElement]()

  private def maybeContinueBoundary(hasC: Boolean, body: List[BStmt]): List[BStmt] =
    if hasC then List(BStmt(Nil, BStmtK.Boundary(body))) else body

  private def maybeBreakBoundary(hasB: Boolean, k: BStmtK): BStmtK =
    if hasB then BStmtK.Boundary(List(BStmt(Nil, k)), label = Some("break$")) else k

  /** `for (init; cond; update) body` → `{ init; while (cond) { body; update } }`.
    * A translated `continue` exits the boundary wrapped around body-without-update,
    * so the update still runs — matching Java's continue-jumps-to-update.
    */
  private def classicFor(f: CtFor): BStmtK =
    val (hasB, hasC) = analyzeLoop(f)
    val init = f.getForInit.asScala.toList.map(s => BStmt(leadingOf(s), stmt(s)))
    val update = f.getForUpdate.asScala.toList.map(s => BStmt(leadingOf(s), stmt(s)))
    val cond = Option(f.getExpression).map(expr).getOrElse(Lit(LitKind.BoolL, "true"))
    val body = maybeContinueBoundary(hasC, blockOf(f.getBody))
    maybeBreakBoundary(hasB, BStmtK.Block(init :+ BStmt(Nil, BStmtK.While(cond, body ++ update))))

  /** `for (T x : e) body` — array-backed iterables get an index loop, everything else
    * the explicit iterator()/hasNext()/next() desugaring (works for any java.lang.Iterable
    * without needing Scala collection conversions). The iterated expression is hoisted so
    * it evaluates once, as in Java.
    */
  private def forEach(f: CtForEach): BStmtK =
    val (hasB, hasC) = analyzeLoop(f)
    val v = f.getVariable
    val x = v.getSimpleName
    val loopVarMutable = Option(f.getBody).exists(writesToVar(_, x))
    val elemT = btype(v.getType)
    val coll = expr(f.getExpression)
    val body = maybeContinueBoundary(hasC, blockOf(f.getBody))
    val isArray = Option(f.getExpression.getType).exists(_.isInstanceOf[CtArrayTypeReference[?]])
    if isArray then
      val arr = x + "$arr"
      val i = x + "$i"
      maybeBreakBoundary(hasB, BStmtK.Block(
        List(
          BStmt(Nil, BStmtK.LocalVar(arr, btype(f.getExpression.getType), Some(coll), effectivelyFinal = true)),
          BStmt(Nil, BStmtK.LocalVar(i, BType.Prim("int"), Some(Lit(LitKind.IntL, "0")), effectivelyFinal = false)),
          BStmt(
            Nil,
            BStmtK.While(
              Binary("<", Ident(i, RefKind.Local), ArrayLength(Ident(arr, RefKind.Local))),
              BStmt(Nil, BStmtK.LocalVar(x, elemT, Some(ArrayAccess(Ident(arr, RefKind.Local), Ident(i, RefKind.Local))), effectivelyFinal = !loopVarMutable))
                :: body
                ::: List(BStmt(Nil, BStmtK.Assign(Ident(i, RefKind.Local), Lit(LitKind.IntL, "1"), Some("+")))),
            ),
          ),
        )
      ))
    else
      val it = x + "$it"
      val itType = BType.Ref("java.util.Iterator", List(elemT))
      maybeBreakBoundary(hasB, BStmtK.Block(
        List(
          BStmt(Nil, BStmtK.LocalVar(it, itType, Some(Call(Recv.On(coll), "iterator", Nil, None, None)), effectivelyFinal = true)),
          BStmt(
            Nil,
            BStmtK.While(
              Call(Recv.On(Ident(it, RefKind.Local)), "hasNext", Nil, None, None),
              BStmt(Nil, BStmtK.LocalVar(x, elemT, Some(Call(Recv.On(Ident(it, RefKind.Local)), "next", Nil, None, None)), effectivelyFinal = !loopVarMutable))
                :: body,
            ),
          ),
        )
      ))

  /** Fallthrough-free switch statements → match. Empty colon-cases group with the next
    * case; a non-terminated non-empty case is genuine fallthrough → Unsupported.
    * A missing default becomes `case _ => ()` (Java's silent fall-past).
    */
  private def switchToMatch(s: CtSwitch[?]): BStmtK =
    val scrutinee = expr(s.getSelector)
    val cases = s.getCases.asScala.toList
    // Java allows `case X: { ...; break; }` — unwrap single-block case bodies so the
    // trailing break is visible
    def effStmts(c: CtCase[?]): List[CtStatement] =
      c.getStatements.asScala.toList match
        case List(b: CtBlock[?]) => b.getStatements.asScala.toList
        case l                   => l
    // mid-case breaks (not trailing) target the switch — encode as a boundary
    val midCaseBreaks = s
      .getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtBreak]))
      .asScala
      .exists { b =>
        (nearestOwner(b, includeSwitch = true) eq s) &&
        !cases.exists(c => effStmts(c).lastOption.exists(_ eq b)) // eq: Spoon equals is structural
      }
    if midCaseBreaks then breakLoops += s
    // pre-compute each case's (bodyStmts, terminated); fallthrough closure = own body
    // ++ the next case's closure when not terminated (tail duplication, RESEARCH §4.2)
    val split = cases.map { c =>
      val stmts = effStmts(c)
      stmts.reverse match
        case (_: CtBreak) :: rest => (rest.reverse, true)
        case all =>
          val terms = all.headOption.exists {
            case _: CtReturn[?] | _: CtThrow => true
            case _                           => false
          }
          (all.reverse, terms)
    }
    val closures = new Array[List[CtStatement]](cases.length)
    for i <- cases.indices.reverse do
      val (body, terminated) = split(i)
      closures(i) =
        if terminated || i == cases.length - 1 then body
        else body ++ closures(i + 1)
    val out = List.newBuilder[BCase]
    var pendingExprs = List.empty[BExpr]
    cases.zipWithIndex.foreach { (c, idx) =>
      val exprs = c.getCaseExpressions.asScala.toList.map(expr)
      val isDefault = exprs.isEmpty
      val isLast = idx == cases.length - 1
      if split(idx)._1.isEmpty && effStmts(c).isEmpty && !isDefault && !isLast then
        pendingExprs = pendingExprs ++ exprs
      else
        out += BCase(pendingExprs ++ exprs, isDefault, block(closures(idx)))
        pendingExprs = Nil
    }
    val result = out.result()
    val withDefault =
      if result.exists(_.isDefault) then result
      else result :+ BCase(Nil, isDefault = true, List(BStmt(Nil, BStmtK.Empty)))
    val m = BStmtK.Match(scrutinee, withDefault)
    if midCaseBreaks then BStmtK.Boundary(List(BStmt(Nil, m))) else m

  /** Any mutation of the named variable inside scope — plain/compound assignment OR
    * increment/decrement (i++ is a write even though Spoon models it as a unary op). */
  private def writesToVar(scope: CtElement, name: String): Boolean =
    scope
      .getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]]))
      .asScala
      .exists { a =>
        a.getAssigned match
          case w: CtVariableWrite[?] => w.getVariable.getSimpleName == name
          case _                     => false
      }
    || scope
      .getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtUnaryOperator[?]]))
      .asScala
      .exists { u =>
        import UnaryOperatorKind.*
        Set(POSTINC, POSTDEC, PREINC, PREDEC).contains(u.getKind) && (u.getOperand match
          case v: CtVariableAccess[?] => v.getVariable.getSimpleName == name
          case _                      => false)
      }

  private def enclosingBodyHasWriteTo(v: CtLocalVariable[?]): Boolean =
    val body = v.getParent(classOf[CtExecutable[?]])
    body != null && writesToVar(body, v.getSimpleName)

  // ---- expressions -----------------------------------------------------------

  private def expr(e: CtExpression[?]): BExpr =
    val core = exprNoCasts(e)
    e.getTypeCasts.asScala.toList.foldRight(core)((t, acc) => Cast(btype(t), acc))

  private def exprNoCasts(e: CtExpression[?]): BExpr = e match
    case l: CtLiteral[?] => literal(l)

    // NOTE: CtFieldRead/CtFieldWrite/CtThisAccess extend CtVariableRead/Write —
    // the specific cases must precede the general variable-access ones.
    case f: CtFieldRead[?]  => fieldAccess(f.getVariable, f.getTarget)
    case f: CtFieldWrite[?] => fieldAccess(f.getVariable, f.getTarget)
    case _: CtThisAccess[?] => This

    case v: CtVariableRead[?]  => varAccess(v.getVariable, v)
    case v: CtVariableWrite[?] => varAccess(v.getVariable, v)

    case a: CtArrayRead[?] => ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression))
    case a: CtArrayWrite[?] => ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression))

    case inv: CtInvocation[?] =>
      val ex = inv.getExecutable
      val recv = inv.getTarget match
        case null                => Recv.OnThis
        case _: CtSuperAccess[?] => Recv.OnSuper
        case ta: CtTypeAccess[?] =>
          // Java resolves statics through subclass names (Parser.staticOfSuper());
          // Scala companions don't inherit — target the DECLARING class
          val owner =
            if ex != null && ex.isStatic then
              Option(ex.getDeclaringType).map(_.getQualifiedName).getOrElse(ta.getAccessedType.getQualifiedName)
            else ta.getAccessedType.getQualifiedName
          Recv.Static(owner)
        case _: CtThisAccess[?] => Recv.OnThis
        case t                  => Recv.On(expr(t))
      val ownerQ = Option(ex.getDeclaringType).map(_.getQualifiedName)
      // Java enum `E.values()` → Scala 3 enum `E.values` (a companion val, no parens);
      // emitting values() parses as values.apply(i). valueOf keeps its arg.
      val isEnumOwner = scala.util.Try(Option(ex.getDeclaringType).map(_.getTypeDeclaration).exists(_.isInstanceOf[CtEnum[?]])).getOrElse(false)
      if ex.getSimpleName == "values" && ex.isStatic && inv.getArguments.isEmpty && isEnumOwner then
        return recv match
          case Recv.Static(owner) => Ident("values", RefKind.StaticField(owner))
          case _                  => Select(This, "values")
      val call = Call(recv, ex.getSimpleName, inv.getArguments.asScala.toList.map(typedArg), formalsOf(ex), ownerQ)
      // a method-generic return (<T> T get(...)) infers Nothing in Scala without a
      // target type — cast to the instantiation javac resolved (erasure-identical)
      val methodGenericReturn = scala.util.Try {
        Option(ex.getExecutableDeclaration).exists { d =>
          d.getType.isInstanceOf[CtTypeParameterReference] &&
          d.asInstanceOf[CtExecutable[?]].isInstanceOf[CtFormalTypeDeclarer] &&
          d.asInstanceOf[CtFormalTypeDeclarer].getFormalCtTypeParameters.asScala
            .exists(_.getSimpleName == d.getType.asInstanceOf[CtTypeParameterReference].getSimpleName)
        }
      }.getOrElse(false)
      val resolved = Option(inv.getType)
      def wildFree(t: BType): Boolean = t match
        case _: BType.Wild    => false
        case BType.Ref(_, as) => as.forall(wildFree)
        case BType.Arr(e2)    => wildFree(e2)
        case _                => true
      // only cast to CONCRETE instantiations — inside the generic method itself the
      // resolved type is just the bound (wildcards), and the polymorphic context
      // carries the type
      if methodGenericReturn && resolved.exists(t => !t.isInstanceOf[CtTypeParameterReference])
      then Cast(btype(resolved.get), call)
      else call

    case na: CtNewArray[?] =>
      val elem = btype(na.getType) match
        case BType.Arr(e) => e
        case t            => t
      val inits = na.getElements.asScala.toList
      val dims = na.getDimensionExpressions.asScala.toList
      if inits.nonEmpty || dims.isEmpty then NewArray(elem, Nil, Some(inits.map(expr))) // `{...}` incl. empty `{}`
      else NewArray(elem, dims.map(expr), None)

    case l: CtLambda[?] =>
      val ps = l.getParameters.asScala.toList
      val params = ps.map(_.getSimpleName)
      // resolved SAM param types (when Spoon infers them) — lets the printer annotate
      // lambda params where an overloaded target defeats Scala's inference
      val ptypes = ps.map(p => scala.util.Try(Option(p.getType).map(btype)).toOption.flatten)
      val paramTypes = if params.nonEmpty && ptypes.forall(_.isDefined) then ptypes.flatten else Nil
      (Option(l.getExpression), Option(l.getBody)) match
        case (Some(e), _)    => Lambda(params, Right(expr(e)), paramTypes)
        case (None, Some(b)) => Lambda(params, Left(block(b.getStatements.asScala.toList)), paramTypes)
        case _               => unsupported(l, "lambda without body")

    case mr: CtExecutableReferenceExpression[?, ?] =>
      val ex = mr.getExecutable
      if ex.isConstructor then
        val formals = Option(ex.getExecutableDeclaration)
          .map(_.getParameters.asScala.toList.map(p => btype(p.getType)))
          .getOrElse(unsupported(mr, "constructor reference with unresolvable formals"))
        btype(ex.getDeclaringType) match
          case r: BType.Ref => return CtorRef(r, formals)
          case t2           => unsupported(mr, s"constructor reference of $t2")
      mr.getTarget match
        case ta: CtTypeAccess[?] if ex.isStatic =>
          MethodRef(Left(ta.getAccessedType.getQualifiedName), ex.getSimpleName)
        case ta: CtTypeAccess[?] =>
          val formals = Option(ex.getExecutableDeclaration)
            .map(_.getParameters.asScala.toList.map(p => btype(p.getType)))
            .getOrElse(unsupported(mr, "unbound method reference with unresolvable formals"))
          UnboundMethodRef(btype(ta.getAccessedType), ex.getSimpleName, formals)
        case t => MethodRef(Right(expr(t)), ex.getSimpleName)

    case nc: CtNewClass[?] =>
      val anonMembers = Option(nc.getAnonymousClass).map(_.getTypeMembers.asScala.toList).getOrElse(Nil)
      val fields = List.newBuilder[BField]
      val methods = List.newBuilder[BMethod]
      val init = List.newBuilder[BStmt]
      anonMembers.foreach {
        case f: CtField[?] =>
          checkAnnotations(f)
          fields += BField(leadingOf(f), mods(f), btype(f.getType), f.getSimpleName, Option(f.getDefaultExpression).map(expr))
        case m: CtMethod[?] => methods += methodDecl(m)
        case c: CtConstructor[?] if c.isImplicit => ()
        // instance-initializer block (double-brace idiom) → statements in the anon body
        case a: CtAnonymousExecutable if !a.getModifiers.asScala.exists(_ == ModifierKind.STATIC) =>
          init ++= block(a.getBody.getStatements.asScala.toList)
        case other => unsupported(other, s"anonymous class member ${other.getClass.getSimpleName}")
      }
      btype(nc.getType) match
        case r: BType.Ref =>
          New(r, nc.getArguments.asScala.toList.map(expr), Some(BAnonBody(fields.result(), methods.result(), init.result())))
        case t => unsupported(nc, s"anonymous class of $t")

    case cc: CtConstructorCall[?] =>
      btype(cc.getType) match
        case r: BType.Ref =>
          New(r, cc.getArguments.asScala.toList.map(typedArg), None, formalsOf(cc.getExecutable))
        case t => unsupported(cc, s"constructor call of $t")

    case b: CtBinaryOperator[?] =>
      if b.getKind == BinaryOperatorKind.INSTANCEOF then
        val tpe = b.getRightHandOperand match
          case ta: CtTypeAccess[?] => btype(ta.getAccessedType)
          case other               => unsupported(other, "instanceof right operand")
        InstanceOf(expr(b.getLeftHandOperand), tpe)
      else
        val concat =
          b.getKind == BinaryOperatorKind.PLUS &&
            Option(b.getType).exists(_.getQualifiedName == "java.lang.String")
        Binary(binOp(b.getKind, b), expr(b.getLeftHandOperand), expr(b.getRightHandOperand), concat)

    case u: CtUnaryOperator[?] =>
      import UnaryOperatorKind.*
      u.getKind match
        case NOT     => Unary("!", expr(u.getOperand))
        case NEG     => Unary("-", expr(u.getOperand))
        case POS     => Unary("+", expr(u.getOperand))
        case COMPL   => Unary("~", expr(u.getOperand))
        case POSTINC => IncDecExpr(expr(u.getOperand), "+", post = true)
        case POSTDEC => IncDecExpr(expr(u.getOperand), "-", post = true)
        case PREINC  => IncDecExpr(expr(u.getOperand), "+", post = false)
        case PREDEC  => IncDecExpr(expr(u.getOperand), "-", post = false)
        case k       => unsupported(u, s"unary operator $k")

    case c: CtConditional[?] =>
      // Java `cond ? null : x` types as x's reference type; Scala infers `X | Null`
      // which won't assign to an unbounded type var — ascribe the null branch to the
      // conditional's resolved type so it stays `null.asInstanceOf[T]`
      val condType = Option(c.getType).filter(t => !t.isPrimitive)
      def branch(e: CtExpression[?], other: CtExpression[?]): BExpr =
        val be = expr(e)
        be match
          case Lit(BExpr.LitKind.NullL, _) =>
            condType.orElse(Option(other.getType).filter(t => !t.isPrimitive)).map(t => Cast(btype(t), be)).getOrElse(be)
          case _ => be
      Ternary(expr(c.getCondition), branch(c.getThenExpression, c.getElseExpression), branch(c.getElseExpression, c.getThenExpression))

    // assignment in expression position: `while ((x = next()) != null)` etc.
    case a: CtAssignment[?, ?] if !a.isInstanceOf[CtOperatorAssignment[?, ?]] =>
      AssignExpr(expr(a.getAssigned), expr(a.getAssignment))

    case ta: CtTypeAccess[?] => ClassLit(btype(ta.getAccessedType))

    case other => unsupported(other, s"expression ${other.getClass.getSimpleName}")

  private def varAccess(ref: CtVariableReference[?], at: CtElement): BExpr = ref match
    case p: CtParameterReference[?] =>
      val varargs = Option(p.getDeclaration).exists(_.isVarArgs)
      Ident(p.getSimpleName, RefKind.Param(varargs))
    case l: CtLocalVariableReference[?] => Ident(l.getSimpleName, RefKind.Local)
    case c: CtCatchVariableReference[?] => Ident(c.getSimpleName, RefKind.Local)
    case other                          => unsupported(at, s"variable reference ${other.getClass.getSimpleName}")

  /** Spoon's getFieldDeclaration can return null for a constant inherited through
    * interfaces/superclasses (BasedSequence.LS where LS is SequenceUtils'), leaving
    * only the ACCESS type. Scala companions don't inherit, so walk the access type's
    * hierarchy — superclass then super-interfaces (sorted for determinism) — to the
    * type that actually DECLARES the field, whose companion object holds the constant. */
  private def declaringTypeOf(start: CtTypeReference[?], simpleName: String): Option[String] =
    val seen = scala.collection.mutable.Set[String]()
    def search(tr: CtTypeReference[?]): Option[String] =
      if tr == null then None
      else
        val q = tr.getQualifiedName
        if q == null || seen(q) then None
        else
          seen += q
          val decl = scala.util.Try(Option(tr.getTypeDeclaration)).toOption.flatten
          if decl.exists(d => scala.util.Try(Option(d.getField(simpleName)).isDefined).getOrElse(false)) then Some(q)
          else
            val supers = decl.toList.flatMap { d =>
              Option(d.getSuperclass).toList ++ d.getSuperInterfaces.asScala.toList.sortBy(_.getQualifiedName)
            }
            supers.iterator.map(search).collectFirst { case Some(x) => x }
    search(start)

  private def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?]): BExpr =
    // Spoon's getDeclaringType returns the ACCESS type for a field inherited through
    // interfaces (BasedSequence.LS where LS is SequenceUtils' constant); Scala companions
    // don't inherit, so resolve to the true declaring type (the field's own declaration).
    val owner = scala.util.Try(Option(ref.getFieldDeclaration).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)).toOption.flatten
      .orElse(Option(ref.getDeclaringType).flatMap(dt => declaringTypeOf(dt, ref.getSimpleName)))
      .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
      .getOrElse(BType.ObjectQ)
    if ref.getSimpleName == "class" then
      target match
        case ta: CtTypeAccess[?] => return ClassLit(btype(ta.getAccessedType))
        case _                   => ()
    if ref.getSimpleName == "length" && Option(ref.getDeclaringType).exists(_.isArray) then
      ArrayLength(expr(target))
    else if target != null && Option(ref.getDeclaringType).exists(_.isArray) then ArrayLength(expr(target))
    else if ref.isStatic then
      // Collections.EMPTY_SET/LIST/MAP are raw-typed constants (java.util.Set, not
      // Set[T]) — Scala rejects the raw type where a parameterized one is expected.
      // The generic empty* methods infer their element type from context.
      val emptyGeneric =
        if owner == "java.util.Collections" then
          ref.getSimpleName match
            case "EMPTY_SET"  => Some("emptySet")
            case "EMPTY_LIST" => Some("emptyList")
            case "EMPTY_MAP"  => Some("emptyMap")
            case _            => None
        else None
      emptyGeneric match
        case Some(m) => Call(Recv.Static(owner), m, Nil, Some(Nil), Some(owner))
        case None    => Ident(ref.getSimpleName, RefKind.StaticField(owner))
    else
      target match
        // this / qualified-this / super targets all print as the bare member in Scala
        case ta: CtThisAccess[?] =>
          // inside a nested class, `this` reads against the OUTER instance (Spoon
          // types the implicit this-access as the outer class) must be re-qualified:
          // `Outer.this.f` for named inners, a bare lexical `f` for anonymous ones —
          // plain `this.f` would resolve against the nested class and miss the field
          outerThisName(ta) match
            case Some("")    => Ident(ref.getSimpleName, RefKind.EnclosingField)
            case Some(outer) => Ident(ref.getSimpleName, RefKind.OuterField(outer))
            case None        => Ident(ref.getSimpleName, RefKind.OwnField)
        case null | (_: CtSuperAccess[?]) =>
          Ident(ref.getSimpleName, RefKind.OwnField)
        case t => Select(expr(t), ref.getSimpleName)

  /** When a this-access inside a named inner class refers to an enclosing type,
    * returns that type's simple name (the `Outer.this` qualifier). None for the
    * ordinary own-instance case, and inside anonymous/local classes (their outer
    * accesses resolve lexically and stay bare — the M2/M3-green encoding). */
  private def outerThisName(ta: CtThisAccess[?]): Option[String] =
    val thisTypeQ = Option(ta.getType).map(_.getQualifiedName)
    var p: CtElement = ta.getParent
    var encl: CtType[?] = null
    while p != null && encl == null do
      p match
        case t: CtType[?] => encl = t
        case _            => ()
      p = p.getParent
    if encl == null then None
    else
      thisTypeQ match
        // the this-access belongs to an ENCLOSING type (its qname differs from the
        // immediate nested class) → it's an outer-field read
        case Some(q) if q != encl.getQualifiedName =>
          if encl.isAnonymous || encl.isLocalType then Some("") // no name → bare lexical
          else if !encl.hasModifier(ModifierKind.STATIC) && encl.getDeclaringType != null then
            val seg = q.substring(q.lastIndexOf('.') + 1)
            Some(seg.substring(seg.lastIndexOf('$') + 1)) // Outer.this.f
          else None
        case _ => None

  /** Java performs unchecked conversion when a raw-typed expression flows into a
    * parameterized target — Scala needs the cast made explicit. */
  private def maybeUncheckedCast(declared: CtTypeReference[?], rhs: CtExpression[?]): BExpr =
    val e = expr(rhs)
    val rt = rhs.getType
    val isRaw = rt != null && !rt.isPrimitive && !rt.isInstanceOf[CtArrayTypeReference[?]] &&
      rt.getActualTypeArguments.isEmpty && rawArity(rt) > 0
    val declaredParameterized = declared != null && !declared.getActualTypeArguments.asScala.isEmpty
    if isRaw && declaredParameterized then Cast(btype(declared), e) else e

  /** Argument wrapped with its resolved static type — the printer's call-site
    * adaptations (array spread, Object[] casts) need it. */
  private def typedArg(e: CtExpression[?]): BExpr =
    val core = expr(e)
    // an explicit cast changes the static type javac used for the call ((Object[]) x
    // into varargs spreads!) — the outermost cast wins over the expression's own type
    val effective = e.getTypeCasts.asScala.lastOption.orElse(Option(e.getType))
    effective match
      case Some(t) => Typed(core, btype(t))
      case None    => core

  private def formalsOf(ex: CtExecutableReference[?]): Option[List[Formal]] =
    Option(ex.getExecutableDeclaration).map { decl =>
      decl.getParameters.asScala.toList.map(p => Formal(btype(p.getType), p.isVarArgs))
    }

  private def literal(l: CtLiteral[?]): BExpr =
    import LitKind.*
    val pos = l.getPosition
    def rawSlice: Option[String] =
      if pos != null && pos.isValidPosition && pos.getSourceEnd >= pos.getSourceStart then
        Some(source.substring(pos.getSourceStart, pos.getSourceEnd + 1))
      else None
    // the position can span a cast prefix ("(Object) 4") — only trust slices that
    // look like the literal itself
    // Java octal escapes (\0, \12, \377) are illegal in Scala — a slice carrying one
    // must fall back to escapeChar/escapeString, which render the resolved value as \uXXXX
    val octalEscape = "\\\\[0-7]".r
    def slice: Option[String] = rawSlice.filter { s0 =>
      val s1 = s0.trim
      octalEscape.findFirstIn(s1).isEmpty && (l.getValue match
        case _: java.lang.String    => s1.startsWith("\"")
        case _: java.lang.Character => s1.startsWith("'")
        case _                      => s1.headOption.exists(c => c.isDigit || c == '-' || c == '.'))
    }
    l.getValue match
      case null                 => Lit(NullL, "null")
      case v: java.lang.String  => Lit(StringL, slice.getOrElse(escapeString(v)))
      case c: java.lang.Character => Lit(CharL, slice.getOrElse(escapeChar(c)))
      case b: java.lang.Boolean => Lit(BoolL, b.toString)
      case n: java.lang.Integer => Lit(IntL, slice.getOrElse(n.toString))
      case n: java.lang.Long    => Lit(LongL, slice.map(s => if s.toLowerCase.endsWith("l") then s else s + "L").getOrElse(n.toString + "L"))
      case n: java.lang.Double  => Lit(DoubleL, slice.getOrElse(n.toString))
      case n: java.lang.Float   => Lit(FloatL, slice.map(s => if s.toLowerCase.endsWith("f") then s else s + "f").getOrElse(n.toString + "f"))
      case v                    => unsupported(l, s"literal $v")

  private def escapeChar(c: Char): String =
    val body = c match
      case '\\' => "\\\\"
      case '\'' => "\\'"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case x if x < 32 || x > 126 => f"\\u$x%04x"
      case x => x.toString
    s"'$body'"

  private def escapeString(v: String): String =
    val sb = new java.lang.StringBuilder("\"")
    v.foreach {
      case '\\' => sb.append("\\\\")
      case '"'  => sb.append("\\\"")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case x if x < 32 || x > 126 => sb.append(f"\\u$x%04x")
      case x => sb.append(x)
    }
    sb.append("\"").toString

  private def binOp(k: BinaryOperatorKind, at: CtElement): String =
    import BinaryOperatorKind.*
    k match
      case PLUS => "+"; case MINUS => "-"; case MUL => "*"; case DIV => "/"; case MOD => "%"
      case AND => "&&"; case OR => "||"
      case BITAND => "&"; case BITOR => "|"; case BITXOR => "^"
      case EQ => "=="; case NE => "!="
      case LT => "<"; case LE => "<="; case GT => ">"; case GE => ">="
      case SL => "<<"; case SR => ">>"; case USR => ">>>"
      case other => unsupported(at, s"binary operator $other")
