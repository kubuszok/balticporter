package balticporter.frontend.spoon

import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.compiler.VirtualFile

import scala.jdk.CollectionConverters.*

/** Populates the typed IR ([[balticporter.tir]]) DIRECTLY from Spoon's resolved model —
  * the re-compiler's build-order step 2. Unlike the BIR frontend, nothing collapses to
  * strings: every declaration mints a stable-identity [[Symbol]], every type reference
  * resolves to a structured [[TypeRepr]] pointing at a symbol, and externals (JDK/library
  * types) are lazily interned so `usagesOf(java.util.List)` works even with no local
  * definition. [[Xref.build]] then indexes every usage by position.
  *
  * Scope: declarations, signatures, and TYPES — the substrate the whole-program transforms
  * query. Method BODIES are not yet translated (the BIR frontend still owns expression
  * fidelity); they surface as `rhs = None`, so term-level (Call) usages are absent for now.
  * Type-position tracing — the point of this pass — is complete, including class/method
  * type-parameter F-bounds.
  */
object SpoonTir:
  /** Build a [[Program]] from already-resolved top-level Spoon types. */
  def fromTypes(types: List[CtType[?]]): Program = new Builder().build(types)

  /** Convenience for tests / snippets: parse one in-memory source (no external classpath;
    * JDK types resolve by qualified name) and populate the TIR from its top-level types. */
  def fromSource(code: String, fileName: String = "Snippet.java"): Program =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setNoClasspath(true)
    launcher.addInputResource(new VirtualFile(code, fileName))
    val model = launcher.buildModel()
    val tops  = model.getAllTypes.asScala.toList.filter(_.getDeclaringType == null)
    fromTypes(tops)

  // -------------------------------------------------------------------------
  /** Interns symbols by a stable string key (qualified names for types, `owner#member`
    * for members, `decl$$Name` for type params). One id per key, monotonic. */
  private final class Minter:
    private var next  = 0
    private val byKey = collection.mutable.Map[String, SymId]()
    private val syms  = collection.mutable.Map[SymId, Symbol]()

    def resolve(key: String): SymId =
      byKey.getOrElseUpdate(key, { val id = SymId(next); next += 1; id })

    def set(id: SymId, sym: Symbol): Unit = syms(id) = sym

    def define(key: String)(mk: SymId => Symbol): SymId =
      val id = resolve(key)
      syms(id) = mk(id)
      id

    /** Ensure a minimal stub exists for an external reference (never clobbers a real
      * definition, so define-after-reference wins). */
    def external(key: String, name: String): SymId =
      val id = resolve(key)
      if !syms.contains(id) then syms(id) = Symbol(id, name, key, Flags(), SymId.None, NoType)
      id

    def table: SymbolTable        = SymbolTable(syms.values)
    def idOf(key: String): SymId  = byKey(key)
    def fullNameOf(id: SymId): String = syms.get(id).map(_.fullName).getOrElse("?")

  private final class Builder:
    private val minter   = new Minter
    private val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()

    def build(types: List[CtType[?]]): Program =
      val units = types.map(classDef)
      new Program(units, minter.table, Xref.build(units))

    // ---- provenance ----
    private def originOf(el: CtElement): Origin =
      val p = el.getPosition
      if p != null && p.isValidPosition then
        Origin(Option(p.getFile).map(_.getPath).getOrElse("<unknown>"), p.getLine, p.getColumn)
      else Origin.synthetic

    private def tt(t: TypeRepr, el: CtElement): TypeTree = TypeTree(t, originOf(el))

    // ---- keys ----
    private def typeKey(t: CtTypeReference[?]): String = t.getQualifiedName
    private def memberKey(owner: SymId, sig: String): String = minterKeyOf(owner) + "#" + sig
    private def minterKeyOf(id: SymId): String = "@" + id.raw // members hang off their owner's id
    private def erasedSig(m: CtExecutable[?]): String =
      val ps = m.getParameters.asScala.toList
        .map(p => scala.util.Try(p.getType.getQualifiedName).getOrElse("?"))
        .mkString(",")
      s"($ps)"

    // ---- type parameter resolution ----
    private def resolveTypeParam(name: String): Option[SymId] =
      tpScopes.iterator.collectFirst { case m if m.contains(name) => m(name) }

    /** Mint ids for all formals FIRST (so bounds can self-reference — F-bounds), then
      * translate each bound with the frame in scope. Returns the frame and the TypeDefs. */
    private def mintTypeParams(declKey: String, owner: SymId, tps: List[CtTypeParameter]): (Map[String, SymId], List[Tree.TypeDef]) =
      val frame = tps.map(tp => tp.getSimpleName -> minter.resolve(declKey + "$$" + tp.getSimpleName)).toMap
      tpScopes.prepend(frame)
      val defs = tps.map { tp =>
        val id     = frame(tp.getSimpleName)
        val bounds = boundsOf(tp)
        minter.set(id, Symbol(id, tp.getSimpleName, declKey + "$$" + tp.getSimpleName, Flags(isParam = true), owner, bounds))
        Tree.TypeDef(id, tt(bounds, tp), originOf(tp))
      }
      tpScopes.remove(0)
      (frame, defs)

    private def boundsOf(tp: CtTypeParameter): TypeBounds =
      Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(tpe) match
        case Some(hi) => TypeBounds(NoType, hi)
        case None     => TypeBounds(NoType, NoType)

    // ---- types ----
    private def tpe(tr: CtTypeReference[?]): TypeRepr = tr match
      case null => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))
      case arr: CtArrayTypeReference[?] =>
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(tpe(arr.getComponentType)))
      case inter: CtIntersectionTypeReference[?] =>
        inter.getBounds.asScala.toList.map(tpe).reduce(AndType(_, _))
      case w: CtWildcardReference =>
        val b = Option(w.getBoundingType).filter(_.getQualifiedName != "java.lang.Object").map(tpe)
        if w.isUpper then TypeBounds(NoType, b.getOrElse(NoType)) else TypeBounds(b.getOrElse(NoType), NoType)
      case tv: CtTypeParameterReference =>
        val id = resolveTypeParam(tv.getSimpleName).getOrElse(minter.external("?" + tv.getSimpleName, tv.getSimpleName))
        TypeRef(NoPrefix, id)
      case p if p.isPrimitive =>
        TypeRef(NoPrefix, minter.external("scala." + primName(p.getSimpleName), p.getSimpleName))
      case r =>
        val head = TypeRef(NoPrefix, typeSym(r))
        r.getActualTypeArguments.asScala.toList match
          case Nil  => head
          case args => AppliedType(head, args.map(tpe))

    /** id of a referenced class type — our own (already defined) or an external stub. */
    private def typeSym(r: CtTypeReference[?]): SymId = minter.external(typeKey(r), r.getSimpleName)

    private def primName(j: String): String = j match
      case "int"     => "Int";  case "long"    => "Long";  case "short"  => "Short"
      case "byte"    => "Byte"; case "char"    => "Char";  case "boolean" => "Boolean"
      case "float"   => "Float"; case "double" => "Double"; case "void"  => "Unit"
      case other     => other.capitalize

    // ---- declarations ----
    private def classDef(t: CtType[?]): Tree.ClassDef =
      val id   = defineType(t)
      val (frame, tpDefs) = mintTypeParams(typeKey(t.getReference), id, t.getFormalCtTypeParameters.asScala.toList)
      tpScopes.prepend(frame)
      val parents = superTypes(t)
      val fields = t.getFields.asScala.toList
        .filterNot(_.isInstanceOf[CtEnumValue[?]])
        .sortBy(posKey)
        .map(fieldDef(id, _))
      val ctors = t match
        case c: CtClass[?] => c.getConstructors.asScala.toList.sortBy(posKey).map(execDef(id, _, "<init>"))
        case _             => Nil
      val methods = t.getMethods.asScala.toList.sortBy(posKey).map(m => execDef(id, m, m.getSimpleName))
      val nested  = t.getNestedTypes.asScala.toList.sortBy(posKey).map(classDef)
      tpScopes.remove(0)
      Tree.ClassDef(id, parents, selfType = None, body = fields ++ ctors ++ methods ++ nested, origin = originOf(t), tparams = tpDefs)

    private def defineType(t: CtType[?]): SymId =
      val q = typeKey(t.getReference)
      minter.define(q)(id => Symbol(id, t.getSimpleName, q, typeFlags(t), ownerSym(t), TypeRef(NoPrefix, id)))

    private def ownerSym(t: CtType[?]): SymId =
      Option(t.getDeclaringType).map(dt => minter.external(typeKey(dt.getReference), dt.getSimpleName)).getOrElse(SymId.None)

    /** superclass (Extends, first) then interfaces (Mixin) — the parent linearization. */
    private def superTypes(t: CtType[?]): List[TypeTree] =
      val sc = t match
        case c: CtClass[?] => Option(c.getSuperclass).filter(_.getQualifiedName != "java.lang.Object")
        case _             => None
      (sc.toList ++ t.getSuperInterfaces.asScala.toList).map(tr => tt(tpe(tr), t))

    private def fieldDef(owner: SymId, f: CtField[?]): Tree.ValDef =
      val ft = tpe(f.getType)
      val id = minter.define(memberKey(owner, f.getSimpleName))(sid =>
        Symbol(sid, f.getSimpleName, qualified(owner, f.getSimpleName), fieldFlags(f), owner, ft)
      )
      // a field initializer is a real expression: translate it so its usages are traced,
      // attributed to the field (not a method).
      val rhs = Option(f.getDefaultExpression).map(e => new BodyTranslator(id, owner).exprOf(e))
      Tree.ValDef(id, tt(ft, f), rhs = rhs, origin = originOf(f))

    private def execDef(owner: SymId, m: CtExecutable[?], name: String): Tree.DefDef =
      val mkey = memberKey(owner, name + erasedSig(m))
      val id   = minter.resolve(mkey)
      val mtps = m match
        case ftd: CtFormalTypeDeclarer => ftd.getFormalCtTypeParameters.asScala.toList
        case _                         => Nil
      val (frame, tpDefs) = mintTypeParams(mkey, id, mtps)
      tpScopes.prepend(frame)
      val bt = new BodyTranslator(id, owner)
      val ps = m.getParameters.asScala.toList
      val pvs = ps.map { p =>
        val pt  = tpe(p.getType)
        val pid = minter.define(minterKeyOf(id) + "%" + p.getSimpleName)(sid =>
          Symbol(sid, p.getSimpleName, qualified(id, p.getSimpleName), Flags(isParam = true), id, pt)
        )
        bt.registerVar(p, pid)
        Tree.ValDef(pid, tt(pt, p), rhs = None, origin = originOf(p))
      }
      val ret = m match
        // a constructor's Spoon type is its declaring class; that is not a return
        // position, so don't record it as a member type — use Unit.
        case _: CtConstructor[?]      => unitT
        case named: CtTypedElement[?] => tpe(named.getType)
        case _                        => unitT
      val sig = MethodType(ps.map(p => p.getSimpleName -> tpe(p.getType)), ret)
      minter.set(id, Symbol(id, name, qualified(owner, name), execFlags(m), owner, sig))
      // translate the body (with param + type-param scope in place) — this is what makes
      // Call / field-ref usages and `callersOf` real. Abstract/interface methods have none.
      val body = Option(m.getBody).map(b => bt.methodBody(b))
      tpScopes.remove(0)
      Tree.DefDef(id, paramss = List(pvs), returnTpt = tt(ret, m), rhs = body, origin = originOf(m), tparams = tpDefs)

    private def qualified(owner: SymId, member: String): String = minter.fullNameOf(owner) + "#" + member
    private def unitT: TypeRepr = TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))

    // ---- flags ----
    private def has(m: CtModifiable, k: ModifierKind): Boolean = m.hasModifier(k)
    import ModifierKind.*

    private def typeFlags(t: CtType[?]): Flags =
      val isTrait = t.isInstanceOf[CtInterface[?]]
      Flags(
        isAbstract = has(t, ABSTRACT) || isTrait,
        isFinal = has(t, FINAL),
        isTrait = isTrait,
        isEnum = t.isInstanceOf[CtEnum[?]],
        isPrivate = has(t, PRIVATE),
        isProtected = has(t, PROTECTED),
        isStatic = has(t, STATIC),
      )

    private def fieldFlags(f: CtField[?]): Flags =
      Flags(
        isFinal = has(f, FINAL),
        isMutable = !has(f, FINAL),
        isStatic = has(f, STATIC),
        isPrivate = has(f, PRIVATE),
        isProtected = has(f, PROTECTED),
      )

    private def execFlags(m: CtExecutable[?]): Flags = m match
      case mod: CtModifiable =>
        Flags(
          isAbstract = has(mod, ABSTRACT),
          isFinal = has(mod, FINAL),
          isStatic = has(mod, STATIC),
          isPrivate = has(mod, PRIVATE),
          isProtected = has(mod, PROTECTED),
        )
      case _ => Flags()

    private def posKey(el: CtElement): Int =
      val p = el.getPosition
      if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue

    private def simpleName(q: String): String =
      val afterDot = q.substring(q.lastIndexOf('.') + 1)
      afterDot.substring(afterDot.lastIndexOf('$') + 1)

    private def unsupported(el: CtElement, what: String): Nothing =
      val p    = el.getPosition
      val line = if p != null && p.isValidPosition then p.getLine.toString else "?"
      val path = if p != null && p.isValidPosition && p.getFile != null then p.getFile.getPath else "<snippet>"
      throw balticporter.core.Unsupported(path, line, what)

    // -----------------------------------------------------------------------
    /** Translates one method/ctor/field-initializer body into TIR terms, resolving every
      * reference to a `SymId`. Covered: locals, assignments, `if`/`while`/`return`/`throw`,
      * blocks, method calls, constructor calls, field/variable access, `this`, casts,
      * ternary, operators (as `x.op(y)` — the quotes.reflect shape), literals. Constructs
      * not yet modeled (for-loops, switch, try, lambdas, arrays, method refs) fail loudly
      * via `Unsupported`, the same anti-omission stance as the BIR frontend — the body node
      * set grows the same way the BIR one did.
      *
      * `classId` is the enclosing class (for `this`); `methodId` owns locals. */
    private final class BodyTranslator(methodId: SymId, classId: SymId):
      private val varIds  = new java.util.IdentityHashMap[CtVariable[?], SymId]()
      private val nameIds = collection.mutable.Map[String, SymId]()

      def registerVar(v: CtVariable[?], id: SymId): Unit =
        varIds.put(v, id); nameIds(v.getSimpleName) = id

      private def nothingT = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
      private def selfT    = TypeRef(NoPrefix, classId)
      private def ty(e: CtTypedElement[?]): TypeRepr = Option(e.getType).map(tpe).getOrElse(NoType)
      private def thisTerm(el: CtElement): Term = Tree.This(classId, selfT, originOf(el))

      /** entry: a method/ctor block → a TIR `Block` (statements, Unit result). */
      def methodBody(b: CtBlock[?]): Term =
        Tree.Block(b.getStatements.asScala.toList.map(stmt), unit(b), unitT, originOf(b))

      def exprOf(e: CtExpression[?]): Term = expr(e)

      private def unit(el: CtElement): Term = Tree.Literal(Constant.UnitC, unitT, originOf(el))

      private def blockTerm(s: CtStatement): Term = s match
        case b: CtBlock[?] => Tree.Block(b.getStatements.asScala.toList.map(stmt), unit(b), unitT, originOf(b))
        case single        => Tree.Block(List(stmt(single)), unit(single), unitT, originOf(single))

      // ---- statements ----
      private def stmt(s: CtStatement): Statement = s match
        case v: CtLocalVariable[?] =>
          val vt  = tpe(v.getType)
          val key = "@" + methodId.raw + "$L$" + v.getSimpleName + "#" + posKey(v)
          val id  = minter.define(key)(sid => Symbol(sid, v.getSimpleName, v.getSimpleName, Flags(), methodId, vt))
          registerVar(v, id)
          Tree.ValDef(id, tt(vt, v), Option(v.getDefaultExpression).map(expr), originOf(v))
        case a: CtOperatorAssignment[?, ?] =>
          val lhs = expr(a.getAssigned)
          Tree.Assign(lhs, binApply(opText(a.getKind), lhs, expr(a.getAssignment), ty(a)), unitT, originOf(a))
        case a: CtAssignment[?, ?] =>
          Tree.Assign(expr(a.getAssigned), expr(a.getAssignment), unitT, originOf(a))
        case i: CtIf =>
          val elze = Option(i.getElseStatement).map(blockTerm).getOrElse(unit(i))
          Tree.If(expr(i.getCondition), blockTerm(i.getThenStatement), elze, unitT, originOf(i))
        case r: CtReturn[?] =>
          Tree.Return(Option(r.getReturnedExpression).map(expr), nothingT, originOf(r))
        case w: CtWhile =>
          Tree.While(expr(w.getLoopingExpression), blockTerm(w.getBody), unitT, originOf(w))
        case t: CtThrow =>
          Tree.Throw(expr(t.getThrownExpression), nothingT, originOf(t))
        case b: CtBlock[?]      => blockTerm(b)
        case inv: CtInvocation[?] => expr(inv)
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          val one = Tree.Literal(Constant.IntC(1), ty(u), originOf(u))
          u.getKind match
            case POSTINC | PREINC => val t = expr(u.getOperand); Tree.Assign(t, binApply("+", t, one, ty(u)), unitT, originOf(u))
            case POSTDEC | PREDEC => val t = expr(u.getOperand); Tree.Assign(t, binApply("-", t, one, ty(u)), unitT, originOf(u))
            case _                => expr(u)
        case other => unsupported(other, s"statement ${other.getClass.getSimpleName}")

      // ---- expressions ----
      private def expr(e: CtExpression[?]): Term =
        val core = exprNoCast(e)
        e.getTypeCasts.asScala.toList.foldRight(core) { (t, acc) =>
          val ct = tpe(t); Tree.Typed(acc, tt(ct, e), ct, originOf(e))
        }

      private def exprNoCast(e: CtExpression[?]): Term = e match
        case l: CtLiteral[?]      => literal(l)
        case f: CtFieldRead[?]    => fieldAccess(f.getVariable, f.getTarget, e)
        case f: CtFieldWrite[?]   => fieldAccess(f.getVariable, f.getTarget, e)
        case _: CtThisAccess[?]   => thisTerm(e)
        case v: CtVariableRead[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case v: CtVariableWrite[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case inv: CtInvocation[?] => invocation(inv)
        case cc: CtConstructorCall[?] => ctorCall(cc)
        case b: CtBinaryOperator[?] =>
          if b.getKind == BinaryOperatorKind.INSTANCEOF then unsupported(b, "instanceof")
          else binApply(opText(b.getKind), expr(b.getLeftHandOperand), expr(b.getRightHandOperand), ty(b))
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          u.getKind match
            case NOT   => unApply("unary_!", expr(u.getOperand), ty(u))
            case NEG   => unApply("unary_-", expr(u.getOperand), ty(u))
            case POS   => unApply("unary_+", expr(u.getOperand), ty(u))
            case COMPL => unApply("unary_~", expr(u.getOperand), ty(u))
            case k     => unsupported(u, s"unary in expression position $k")
        case c: CtConditional[?] =>
          Tree.If(expr(c.getCondition), expr(c.getThenExpression), expr(c.getElseExpression), ty(c), originOf(c))
        case ta: CtTypeAccess[?] => Tree.Literal(Constant.ClassOfC(tpe(ta.getAccessedType)), ty(e), originOf(e))
        case other => unsupported(other, s"expression ${other.getClass.getSimpleName}")

      private def literal(l: CtLiteral[?]): Term =
        val c: Constant = l.getValue match
          case null                    => Constant.NullC
          case b: java.lang.Boolean    => Constant.BoolC(b)
          case ch: java.lang.Character => Constant.CharC(ch)
          case s: java.lang.String     => Constant.StringC(s)
          case n: java.lang.Integer    => Constant.IntC(n)
          case n: java.lang.Long       => Constant.LongC(n)
          case n: java.lang.Double     => Constant.DoubleC(n)
          case n: java.lang.Float      => Constant.FloatC(n)
          case n: java.lang.Byte       => Constant.ByteC(n)
          case n: java.lang.Short      => Constant.ShortC(n)
          case other                   => unsupported(l, s"literal ${other.getClass.getSimpleName}")
        Tree.Literal(c, ty(l), originOf(l))

      private def resolveVar(ref: CtVariableReference[?]): SymId =
        val decl = Option(ref.getDeclaration).orNull
        if decl != null && varIds.containsKey(decl) then varIds.get(decl)
        else nameIds.getOrElse(ref.getSimpleName, minter.external("?var$" + ref.getSimpleName, ref.getSimpleName))

      private def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?], at: CtExpression[?]): Term =
        if ref.getSimpleName == "class" then Tree.Literal(Constant.ClassOfC(ty(at)), ty(at), originOf(at))
        else
          val fid = fieldSym(ref)
          val qual: Term = target match
            case null | (_: CtThisAccess[?]) | (_: CtSuperAccess[?]) => thisTerm(at)
            case ta: CtTypeAccess[?]                                 => typeTerm(ta, at) // static access
            case other                                               => expr(other)
          Tree.Select(qual, fid, ty(at), originOf(at))

      private def fieldSym(ref: CtFieldReference[?]): SymId =
        val ownerQ = Option(ref.getFieldDeclaration).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)
          .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
          .getOrElse("java.lang.Object")
        val ownerId = minter.external(ownerQ, simpleName(ownerQ))
        minter.external(memberKey(ownerId, ref.getSimpleName), ref.getSimpleName)

      private def invocation(inv: CtInvocation[?]): Term =
        val ex   = inv.getExecutable
        val mid  = methodSym(ex)
        val args = inv.getArguments.asScala.toList.map(expr)
        val recv: Term = inv.getTarget match
          case null | (_: CtThisAccess[?]) | (_: CtSuperAccess[?]) => thisTerm(inv)
          case ta: CtTypeAccess[?]                                 => typeTerm(ta, inv) // static call
          case t                                                   => expr(t)
        Tree.Apply(Tree.Select(recv, mid, NoType, originOf(inv)), args, mid, ty(inv), originOf(inv))

      private def ctorCall(cc: CtConstructorCall[?]): Term =
        val t    = tpe(cc.getType)
        val cid  = methodSym(cc.getExecutable)
        val args = cc.getArguments.asScala.toList.map(expr)
        Tree.Apply(Tree.New(tt(t, cc), t, originOf(cc)), args, cid, t, originOf(cc))

      /** SymId of a called executable — via its declaration (keyed identically to how we
        * define our own methods, so call sites and defs share one symbol) or, for
        * unresolved externals, by its reference. */
      private def methodSym(ex: CtExecutableReference[?]): SymId =
        Option(ex.getExecutableDeclaration) match
          case Some(decl) =>
            val (q, s) = declType(decl)
            val ownerId = minter.external(q, s)
            val nm      = if decl.isInstanceOf[CtConstructor[?]] then "<init>" else decl.getSimpleName
            minter.external(memberKey(ownerId, nm + erasedSig(decl)), nm)
          case None =>
            val ownerQ  = Option(ex.getDeclaringType).map(_.getQualifiedName).getOrElse("java.lang.Object")
            val ownerId = minter.external(ownerQ, simpleName(ownerQ))
            val nm      = if ex.isConstructor then "<init>" else ex.getSimpleName
            val sig     = ex.getParameters.asScala.toList.map(p => scala.util.Try(p.getQualifiedName).getOrElse("?")).mkString(",")
            minter.external(memberKey(ownerId, s"$nm($sig)"), nm)

      private def declType(decl: CtExecutable[?]): (String, String) = decl match
        case tm: CtTypeMember if tm.getDeclaringType != null => (tm.getDeclaringType.getQualifiedName, tm.getDeclaringType.getSimpleName)
        case _ =>
          val t = decl.getParent(classOf[CtType[?]])
          if t != null then (t.getQualifiedName, t.getSimpleName) else ("java.lang.Object", "Object")

      private def typeTerm(ta: CtTypeAccess[?], at: CtElement): Term =
        val q  = ta.getAccessedType.getQualifiedName
        val id = minter.external(q, simpleName(q))
        Tree.Ident(id, TypeRef(NoPrefix, id), originOf(at))

      // operators as `recv.op(args)` — the quotes.reflect shape (no dedicated node).
      private def opId(op: String): SymId = minter.external("scala.<op>#" + op, op)
      private def binApply(op: String, l: Term, r: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(l, opId(op), NoType, l.origin), List(r), opId(op), resT, l.origin)
      private def unApply(op: String, o: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(o, opId(op), NoType, o.origin), Nil, opId(op), resT, o.origin)

      private def opText(k: BinaryOperatorKind): String =
        import BinaryOperatorKind.*
        k match
          case PLUS => "+"; case MINUS => "-"; case MUL => "*"; case DIV => "/"; case MOD => "%"
          case AND => "&&"; case OR => "||"; case BITAND => "&"; case BITOR => "|"; case BITXOR => "^"
          case EQ => "=="; case NE => "!="; case LT => "<"; case LE => "<="; case GT => ">"; case GE => ">="
          case SL => "<<"; case SR => ">>"; case USR => ">>>"
          case other => "?" + other
