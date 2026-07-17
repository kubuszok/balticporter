package balticporter.frontend.spoon

import balticporter.core.*
import balticporter.core.BExpr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Frontend on Spoon 11.x (ECJ underneath, full-classpath mode, comments enabled).
  * The only module that sees Spoon types (PLAN.md §2 insulation rule).
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

  private def leadingOf(el: CtElement): List[Trivia] =
    el.getComments.asScala.toList.map(triviaOf)

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
      BType.Ref(r.getQualifiedName, args)

  // ---- declarations ----------------------------------------------------------

  def build(types: List[CtType[?]]): BUnit =
    val pkg = types.headOption.flatMap(t => Option(t.getPackage)).map(_.getQualifiedName).getOrElse("")
    BUnit(sourcePath, pkg, types.map(typeDecl), CommentScanner.scan(source))

  private def mods(m: CtModifiable, isOverride: Boolean = false): Mods =
    val vis =
      if m.hasModifier(ModifierKind.PUBLIC) then Vis.Public
      else if m.hasModifier(ModifierKind.PROTECTED) then Vis.Protected
      else if m.hasModifier(ModifierKind.PRIVATE) then Vis.Private
      else Vis.PackagePrivate
    Mods(
      vis = vis,
      isAbstract = m.hasModifier(ModifierKind.ABSTRACT),
      isFinal = m.hasModifier(ModifierKind.FINAL),
      isStatic = m.hasModifier(ModifierKind.STATIC),
      isOverride = isOverride,
    )

  private val ignoredAnnotations = Set(
    "java.lang.Override", "java.lang.SuppressWarnings", "java.lang.SafeVarargs",
    "java.lang.FunctionalInterface", // Scala SAM conversion needs no marker
  )

  private def checkAnnotations(el: CtElement & CtModifiable): Boolean =
    var hasOverride = false
    el.getAnnotations.asScala.foreach { a =>
      val q = a.getAnnotationType.getQualifiedName
      if q == "java.lang.Override" then hasOverride = true
      // Jackson annotations: dropped — serialization is replaced per project dispositions
      // (ssg: Jackson → LiquidSupport trait; see docs/architecture/liqp-port.md).
      else if !ignoredAnnotations.contains(q) && !q.startsWith("com.fasterxml.jackson.") then
        unsupported(el, s"annotation @$q")
    }
    hasOverride

  private def typeDecl(t: CtType[?]): BTypeDecl = t match
    case c: CtClass[?] =>
      checkAnnotations(c)
      val (svuid, fields, staticFields) = extractFields(c)
      val (staticM, instanceM) = c.getMethods.asScala.toList.sortBy(posKey).partition(_.hasModifier(ModifierKind.STATIC))
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
        nested = nestedOf(c),
        serialVersionUID = svuid,
      )
    case i: CtInterface[?] =>
      checkAnnotations(i)
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
        nested = nestedOf(i),
        serialVersionUID = svuid,
      )
    case other => unsupported(other, s"type kind ${other.getClass.getSimpleName}")

  private def nestedOf(t: CtType[?]): List[BTypeDecl] =
    val nested = t.getNestedTypes.asScala.toList
    if nested.nonEmpty then unsupported(nested.head, "nested type (not in M0 subset)")
    Nil

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
    c.getFields.asScala.toList.foreach { f =>
      checkAnnotations(f)
      val isStatic = f.hasModifier(ModifierKind.STATIC) || implicitlyStatic
      if f.getSimpleName == "serialVersionUID" && isStatic then
        svuid = Option(f.getDefaultExpression).collect { case l: CtLiteral[?] =>
          l.getValue match
            case n: java.lang.Number => n.longValue
            case v                   => unsupported(f, s"serialVersionUID value $v")
        }
      else
        val bf = BField(
          leading = leadingOf(f),
          mods = mods(f),
          tpe = btype(f.getType),
          name = f.getSimpleName,
          init = Option(f.getDefaultExpression).map(expr),
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
    val (superArgs, thisArgs, rest) = stmts match
      case (inv: CtInvocation[?]) :: tail if inv.getExecutable != null && inv.getExecutable.isConstructor =>
        val ownerQ = Option(inv.getExecutable.getDeclaringType).map(_.getQualifiedName)
        val isThisCall = ownerQ.contains(c.getDeclaringType.getQualifiedName)
        val args = inv.getArguments.asScala.toList.map(expr)
        if isThisCall then (None, Some(args), tail) else (Some(args), None, tail)
      case _ => (None, None, stmts)
    BCtor(
      leading = leadingOf(c),
      mods = mods(c),
      params = paramsOf(c),
      superArgs = superArgs,
      thisArgs = thisArgs,
      body = block(rest),
    )

  private def methodDecl(m: CtMethod[?]): BMethod =
    val isOverride = checkAnnotations(m)
    val assigned = collection.mutable.Set[String]()
    Option(m.getBody).foreach { b =>
      b.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]])).asScala.foreach { a =>
        a.getAssigned match
          case w: CtVariableWrite[?] =>
            w.getVariable match
              case p: CtParameterReference[?] => assigned += p.getSimpleName
              case _                          => ()
          case _ => ()
      }
    }
    BMethod(
      leading = leadingOf(m),
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
        out += BStmt(pending ++ leadingOf(s), stmt(s))
        pending = Nil
    }
    if pending.nonEmpty then out += BStmt(pending, BStmtK.Empty)
    out.result()

  private def blockOf(s: CtStatement): List[BStmt] = s match
    case null        => Nil
    case b: CtBlock[?] => block(b.getStatements.asScala.toList)
    case single      => List(BStmt(leadingOf(single), stmt(single)))

  private def stmt(s: CtStatement): BStmtK = s match
    case v: CtLocalVariable[?] =>
      val reassigned = enclosingBodyHasWriteTo(v)
      BStmtK.LocalVar(v.getSimpleName, btype(v.getType), Option(v.getDefaultExpression).map(expr), !reassigned)
    case a: CtOperatorAssignment[?, ?] =>
      BStmtK.Assign(expr(a.getAssigned), expr(a.getAssignment), Some(binOp(a.getKind, a)))
    case a: CtAssignment[?, ?] =>
      BStmtK.Assign(expr(a.getAssigned), expr(a.getAssignment), None)
    case i: CtIf =>
      BStmtK.If(expr(i.getCondition), blockOf(i.getThenStatement), Option(i.getElseStatement).map(blockOf))
    case r: CtReturn[?] =>
      BStmtK.Return(Option(r.getReturnedExpression).map(expr))
    case t: CtThrow =>
      BStmtK.Throw(expr(t.getThrownExpression))
    case w: CtWhile =>
      BStmtK.While(expr(w.getLoopingExpression), blockOf(w.getBody))
    case b: CtBlock[?] =>
      BStmtK.Block(block(b.getStatements.asScala.toList))
    case i: CtInvocation[?] =>
      BStmtK.ExprStmt(expr(i))

    case f: CtForEach => forEach(f)
    case f: CtFor     => classicFor(f)

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

    case s: CtSwitch[?] => switchToMatch(s)

    // i++ / i-- / ++i / --i in statement position → i += 1 / i -= 1
    case u: CtUnaryOperator[?] =>
      import UnaryOperatorKind.*
      u.getKind match
        case POSTINC | PREINC => BStmtK.Assign(expr(u.getOperand), Lit(LitKind.IntL, "1"), Some("+"))
        case POSTDEC | PREDEC => BStmtK.Assign(expr(u.getOperand), Lit(LitKind.IntL, "1"), Some("-"))
        case _                => BStmtK.ExprStmt(expr(u))

    case other => unsupported(other, s"statement ${other.getClass.getSimpleName}")

  /** `for (init; cond; update) body` → `{ init; while (cond) { body; update } }`.
    * Safe because `continue` is not (yet) translated — nothing can skip the update.
    */
  private def classicFor(f: CtFor): BStmtK =
    val init = f.getForInit.asScala.toList.map(s => BStmt(leadingOf(s), stmt(s)))
    val update = f.getForUpdate.asScala.toList.map(s => BStmt(leadingOf(s), stmt(s)))
    val cond = Option(f.getExpression).map(expr).getOrElse(Lit(LitKind.BoolL, "true"))
    BStmtK.Block(init :+ BStmt(Nil, BStmtK.While(cond, blockOf(f.getBody) ++ update)))

  /** `for (T x : e) body` — array-backed iterables get an index loop, everything else
    * the explicit iterator()/hasNext()/next() desugaring (works for any java.lang.Iterable
    * without needing Scala collection conversions). The iterated expression is hoisted so
    * it evaluates once, as in Java.
    */
  private def forEach(f: CtForEach): BStmtK =
    val v = f.getVariable
    val x = v.getSimpleName
    val elemT = btype(v.getType)
    val coll = expr(f.getExpression)
    val body = blockOf(f.getBody)
    val isArray = Option(f.getExpression.getType).exists(_.isInstanceOf[CtArrayTypeReference[?]])
    if isArray then
      val arr = x + "$arr"
      val i = x + "$i"
      BStmtK.Block(
        List(
          BStmt(Nil, BStmtK.LocalVar(arr, btype(f.getExpression.getType), Some(coll), effectivelyFinal = true)),
          BStmt(Nil, BStmtK.LocalVar(i, BType.Prim("int"), Some(Lit(LitKind.IntL, "0")), effectivelyFinal = false)),
          BStmt(
            Nil,
            BStmtK.While(
              Binary("<", Ident(i, RefKind.Local), ArrayLength(Ident(arr, RefKind.Local))),
              BStmt(Nil, BStmtK.LocalVar(x, elemT, Some(ArrayAccess(Ident(arr, RefKind.Local), Ident(i, RefKind.Local))), effectivelyFinal = true))
                :: body
                ::: List(BStmt(Nil, BStmtK.Assign(Ident(i, RefKind.Local), Lit(LitKind.IntL, "1"), Some("+")))),
            ),
          ),
        )
      )
    else
      val it = x + "$it"
      val itType = BType.Ref("java.util.Iterator", List(elemT))
      BStmtK.Block(
        List(
          BStmt(Nil, BStmtK.LocalVar(it, itType, Some(Call(Recv.On(coll), "iterator", Nil, None, None)), effectivelyFinal = true)),
          BStmt(
            Nil,
            BStmtK.While(
              Call(Recv.On(Ident(it, RefKind.Local)), "hasNext", Nil, None, None),
              BStmt(Nil, BStmtK.LocalVar(x, elemT, Some(Call(Recv.On(Ident(it, RefKind.Local)), "next", Nil, None, None)), effectivelyFinal = true))
                :: body,
            ),
          ),
        )
      )

  /** Fallthrough-free switch statements → match. Empty colon-cases group with the next
    * case; a non-terminated non-empty case is genuine fallthrough → Unsupported.
    * A missing default becomes `case _ => ()` (Java's silent fall-past).
    */
  private def switchToMatch(s: CtSwitch[?]): BStmtK =
    val scrutinee = expr(s.getSelector)
    val out = List.newBuilder[BCase]
    var pendingExprs = List.empty[BExpr]
    val cases = s.getCases.asScala.toList
    cases.zipWithIndex.foreach { (c, idx) =>
      val exprs = c.getCaseExpressions.asScala.toList.map(expr)
      val isDefault = exprs.isEmpty
      val stmts = c.getStatements.asScala.toList
      val isLast = idx == cases.length - 1
      if stmts.isEmpty && !isDefault && !isLast then pendingExprs = pendingExprs ++ exprs
      else
        val (bodyStmts, terminated) = stmts.reverse match
          case (_: CtBreak) :: rest => (rest.reverse, true)
          case all =>
            val terms = all.headOption.exists {
              case _: CtReturn[?] | _: CtThrow => true
              case _                           => false
            }
            (all.reverse, terms)
        if !terminated && !isLast then unsupported(c, "switch fallthrough")
        out += BCase(pendingExprs ++ exprs, isDefault, block(bodyStmts))
        pendingExprs = Nil
    }
    val result = out.result()
    val withDefault =
      if result.exists(_.isDefault) then result
      else result :+ BCase(Nil, isDefault = true, List(BStmt(Nil, BStmtK.Empty)))
    BStmtK.Match(scrutinee, withDefault)

  private def enclosingBodyHasWriteTo(v: CtLocalVariable[?]): Boolean =
    val body = v.getParent(classOf[CtExecutable[?]])
    body != null && body.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]])).asScala.exists { a =>
      a.getAssigned match
        case w: CtVariableWrite[?] =>
          w.getVariable match
            case l: CtLocalVariableReference[?] => l.getSimpleName == v.getSimpleName
            case _                              => false
        case _ => false
    }

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

    case inv: CtInvocation[?] =>
      val ex = inv.getExecutable
      val recv = inv.getTarget match
        case null                              => Recv.OnThis
        case _: CtSuperAccess[?]               => Recv.OnSuper
        case ta: CtTypeAccess[?]               => Recv.Static(ta.getAccessedType.getQualifiedName)
        case _: CtThisAccess[?]                => Recv.OnThis
        case t                                 => Recv.On(expr(t))
      val ownerQ = Option(ex.getDeclaringType).map(_.getQualifiedName)
      Call(recv, ex.getSimpleName, inv.getArguments.asScala.toList.map(expr), formalsOf(ex), ownerQ)

    case na: CtNewArray[?] =>
      val elem = btype(na.getType) match
        case BType.Arr(e) => e
        case t            => t
      val inits = na.getElements.asScala.toList
      val dims = na.getDimensionExpressions.asScala.toList
      if inits.nonEmpty || dims.isEmpty then NewArray(elem, Nil, Some(inits.map(expr))) // `{...}` incl. empty `{}`
      else NewArray(elem, dims.map(expr), None)

    case l: CtLambda[?] =>
      val params = l.getParameters.asScala.toList.map(_.getSimpleName)
      (Option(l.getExpression), Option(l.getBody)) match
        case (Some(e), _)    => Lambda(params, Right(expr(e)))
        case (None, Some(b)) => Lambda(params, Left(block(b.getStatements.asScala.toList)))
        case _               => unsupported(l, "lambda without body")

    case cc: CtConstructorCall[?] =>
      btype(cc.getType) match
        case r: BType.Ref => New(r, cc.getArguments.asScala.toList.map(expr))
        case t            => unsupported(cc, s"constructor call of $t")

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
        case NOT  => Unary("!", expr(u.getOperand))
        case NEG  => Unary("-", expr(u.getOperand))
        case POS  => Unary("+", expr(u.getOperand))
        case COMPL => Unary("~", expr(u.getOperand))
        case k    => unsupported(u, s"unary operator $k")

    case c: CtConditional[?] => Ternary(expr(c.getCondition), expr(c.getThenExpression), expr(c.getElseExpression))

    case ta: CtTypeAccess[?] => ClassLit(btype(ta.getAccessedType))

    case other => unsupported(other, s"expression ${other.getClass.getSimpleName}")

  private def varAccess(ref: CtVariableReference[?], at: CtElement): BExpr = ref match
    case p: CtParameterReference[?] =>
      val varargs = Option(p.getDeclaration).exists(_.isVarArgs)
      Ident(p.getSimpleName, RefKind.Param(varargs))
    case l: CtLocalVariableReference[?] => Ident(l.getSimpleName, RefKind.Local)
    case c: CtCatchVariableReference[?] => Ident(c.getSimpleName, RefKind.Local)
    case other                          => unsupported(at, s"variable reference ${other.getClass.getSimpleName}")

  private def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?]): BExpr =
    val owner = Option(ref.getDeclaringType).map(_.getQualifiedName).getOrElse(BType.ObjectQ)
    if ref.getSimpleName == "length" && Option(ref.getDeclaringType).exists(_.isArray) then
      ArrayLength(expr(target))
    else if target != null && Option(ref.getDeclaringType).exists(_.isArray) then ArrayLength(expr(target))
    else if ref.isStatic then Ident(ref.getSimpleName, RefKind.StaticField(owner))
    else
      target match
        case null | (_: CtThisAccess[?]) => Ident(ref.getSimpleName, RefKind.OwnField)
        case t                           => Select(expr(t), ref.getSimpleName)

  private def formalsOf(ex: CtExecutableReference[?]): Option[List[Formal]] =
    Option(ex.getExecutableDeclaration).map { decl =>
      decl.getParameters.asScala.toList.map(p => Formal(btype(p.getType), p.isVarArgs))
    }

  private def literal(l: CtLiteral[?]): BExpr =
    import LitKind.*
    val pos = l.getPosition
    def slice: Option[String] =
      if pos != null && pos.isValidPosition && pos.getSourceEnd >= pos.getSourceStart then
        Some(source.substring(pos.getSourceStart, pos.getSourceEnd + 1))
      else None
    l.getValue match
      case null                 => Lit(NullL, "null")
      case _: java.lang.String  => Lit(StringL, slice.getOrElse(unsupported(l, "string literal without position")))
      case _: java.lang.Character => Lit(CharL, slice.getOrElse(unsupported(l, "char literal without position")))
      case b: java.lang.Boolean => Lit(BoolL, b.toString)
      case n: java.lang.Integer => Lit(IntL, slice.getOrElse(n.toString))
      case n: java.lang.Long    => Lit(LongL, slice.map(s => if s.toLowerCase.endsWith("l") then s else s + "L").getOrElse(n.toString + "L"))
      case n: java.lang.Double  => Lit(DoubleL, slice.getOrElse(n.toString))
      case n: java.lang.Float   => Lit(FloatL, slice.map(s => if s.toLowerCase.endsWith("f") then s else s + "f").getOrElse(n.toString + "f"))
      case v                    => unsupported(l, s"literal $v")

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
