package balticporter.frontend.spoon

// Split out of SpoonTir.scala for file size (context diet S2): BodyTranslator's expression-side lowering.

import balticporter.core.{AnnotationPolicy, FrontendConfig, RealPath, Substituted, Substitutions}
import balticporter.catalog.{CatalogLog, Dispatch, JS, Lowering, Obligations, Typing}
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.adaption.TypeAdaptor
import spoon.support.compiler.VirtualFile

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*
import balticporter.frontend.spoon.SpoonTir.TypeShape

private[spoon] trait SpoonTirBodyExprs:
  this: SpoonTirBodyCore & SpoonTirBodyStmts =>

  // ---- expressions ----
  /** the casts the SOURCE wrote, applied innermost-first. `(T) x` is `x.asInstanceOf[T]` except
    * a boxed-WRAPPER operand cast to a primitive, which is a CONVERSION in java (JLS 5.1.8 then
    * 5.1.2) — probed javac/scalac 3.8.4 over all 45 cells. `Object`/`Number` excluded (java
    * performs no dispatch there, JLS 5.5); same-type unbox left to `Predef` ([[coerce]]). */
  private[spoon] def expr(e: CtExpression[?]): Term =
    val core  = exprNoCast(e)
    val casts = e.getTypeCasts.asScala.toList
    val et0: CtTypeReference[?] = e.getType
    // the fold carries the type the term HAS at each step: `e.getType` under the innermost
    // cast, and thereafter the cast below the one being applied.
    casts.foldRight((core, et0)) { case (t, (acc, src)) =>
      (castOf(t, src, acc, e), t: CtTypeReference[?])
    }._1

  /** one source cast, as the conversion or the assertion java performs there. See [[expr]]. */
  private[spoon] def castOf(target: CtTypeReference[?], src: CtTypeReference[?], acc: Term,
                     e: CtExpression[?]): Term =
    val unboxing = target != null && target.isPrimitive && src != null && !src.isPrimitive &&
      wrapperOf.values.toSet(src.getQualifiedName) &&
      wrapperOf.get(target.getSimpleName).exists(_ != src.getQualifiedName)
    if unboxing then unbox(acc, src.getQualifiedName, target.getSimpleName, e)
    else
      val ct = tpe(target); Tree.Typed(acc, tt(ct, e), ct, originOf(e))

  /** THE TYPE AN EXPRESSION HAS WHERE IT STANDS — after the source's own casts, at the OUTERMOST
    * one, the HEAD of `getTypeCasts` ([[expr]] folds `foldRight`, so the head is the OUTER
    * `Tree.Typed`, matching java's order). ONE function, six callers (CLAUDE.md §4.6,
    * ENGINE-LIMITS F8) — the idiom was written six times taking `lastOption`, the INNERMOST
    * cast, silently wrong. `null` where Spoon has no answer; callers decline honestly. */
  private[spoon] def castType(e: CtExpression[?]): CtTypeReference[?] =
    e.getTypeCasts.asScala.headOption.getOrElse(e.getType)

  /** THE EXPRESSION DISPATCH — the wrapper's second entry, symmetrical with [[stmtKind]] and
    * for the same reason. See that method for why it is here and not in the arms. */
  private[spoon] def exprNoCast(e: CtExpression[?]): Term =
    Lowering.of(SpoonKinds.nameOf(e.getClass), Dispatch.Expression, originOf(e), e)(exprArm(e))

  private[spoon] def exprArm(e: CtExpression[?])(using Obligations): Term = e match
    case l: CtLiteral[?]      => literal(l)
    case f: CtFieldRead[?]    => fieldAccess(f.getVariable, f.getTarget, e)
    case f: CtFieldWrite[?]   => fieldAccess(f.getVariable, f.getTarget, e)
    // `Outer.this` USED AS A VALUE (`listener.keyTyped(TextField.this, c)` from an inner
    // listener): Scala's bare `this` names the INNERMOST class, so the enclosing instance has
    // to be named explicitly. Carry the enclosing class's symbol; the emitter qualifies it.
    case ta: CtThisAccess[?] if !isOwnThis(ta) && outerThis(ta).isDefined => outerThis(ta).get
    case ta: CtThisAccess[?]  => thisOf(ta, e)
    case v: CtVariableRead[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
    case v: CtVariableWrite[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
    case inv: CtInvocation[?] => invocation(inv)
    case cc: CtConstructorCall[?] => ctorCall(cc)
    case a: CtArrayRead[?]  => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
    case a: CtArrayWrite[?] => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
    case na: CtNewArray[?]  => newArray(na)
    case l: CtLambda[?]     => lambda(l)
    case mr: CtExecutableReferenceExpression[?, ?] => methodRef(mr)
    case b: CtBinaryOperator[?] =>
      // BOTH consults happen at EVERY binary operator, `instanceof` included — the catalog
      // attaches them to the NODE KIND, and asking only where the answer is already known would
      // discharge the obligation on a condition of its own choosing. Neither predicate touches
      // an operand until it has ruled the kind in, so asking everywhere costs one `getKind`
      // comparison and translates nothing twice.
      val stringified = Obligations.consult(JS.E(14), originOf(b))(stringConcatLeft(b))
      val identity    = Obligations.consult(JS.E(1), originOf(b))(referenceIdentity(b))
      if b.getKind == BinaryOperatorKind.INSTANCEOF then
        b.getRightHandOperand match
          case ta: CtTypeAccess[?] =>
            val tp = tpe(ta.getAccessedType)
            Tree.InstanceOf(expr(b.getLeftHandOperand), tt(tp, b), ty(b), originOf(b))
          // SE16's PATTERN form — `o instanceof String s`, JLS 15.20.2. REFUSED: the pattern is
          // not the gap (`case s: T =>` binds perfectly), the SCOPE is — JLS 6.3.1 gives the
          // binding a FLOW scope AFTER the `if`, so no lexical `val` is faithful (a hoisted `var`
          // diverges under CAPTURE, §4.4). MARKED rather than thrown: the whole `instanceof` is a
          // boolean EXPRESSION, a shape a term marker takes exactly (ENGINE-LIMITS T18).
          case p: CtPattern =>
            unlowered(b, s"an `instanceof` PATTERN binding — ${SpoonKinds.nameOf(p.getClass)} " +
              "(JLS 15.20.2). Java's binding is FLOW-SCOPED (JLS 6.3.1), so no lexical `val` " +
              "placement is faithful and a hoisted `var` diverges under capture; see " +
              "ENGINE-LIMITS T18 for the three placements measured", ty(b), about = p)
          case other => unsupported(other, "instanceof right operand")
      else
        stringified.orElse(identity).getOrElse(
          opText(b.getKind).fold(unknownOp(b.getKind, b, ty(b)))(
            op => binApply(op, expr(b.getLeftHandOperand), expr(b.getRightHandOperand), ty(b))))
    case u: CtUnaryOperator[?] =>
      import UnaryOperatorKind.*
      // JS-E02, consulted at every unary operator for the reason above; `incDecOf` answers
      // `scala.None` for the four that are not increments.
      Obligations.consult(JS.E(2), originOf(u))(incDecOf(u)).getOrElse(u.getKind match
        case NOT     => unApply("unary_!", expr(u.getOperand), ty(u))
        case NEG     => unApply("unary_-", expr(u.getOperand), ty(u))
        case POS     => unApply("unary_+", expr(u.getOperand), ty(u))
        case COMPL   => unApply("unary_~", expr(u.getOperand), ty(u))
        // Java has eight unary operators; `incDecOf` above answers four, this match the other
        // four — default is unreachable TODAY, but for SPOON, not for Java: `getKind` is a java
        // enum scalac cannot check, so an upgrade adding a kind produces a `MatchError` with no
        // origin (§4.45). Default is a MARKER, `FrontendBlindSpot` (not `UnmodelledNodeKind`,
        // since the kind IS dispatched on here — what is missing is one shape of it).
        case other =>
          unlowered(u, s"unary operator kind '$other' — this arm enumerates java's eight and " +
            "the parser produced a ninth", ty(u), Some(UnportableKind.FrontendBlindSpot)))
    // assignment used as a VALUE (`return a = v`, `while ((line = read()) != null)`):
    // Java yields the assigned value, Scala's `=` is Unit — lower to `{ lhs = rhs; lhs }`.
    case a: CtOperatorAssignment[?, ?] =>
      val lhs = expr(a.getAssigned)
      val rhs = expr(a.getAssignment)
      val op  = opText(a.getKind).getOrElse { unknownOp(a.getKind, a, ty(a)); "?" }
      // JS-E04 — the same difference as JS-E03 at the other dispatch, and the same PREDICATE.
      // `compoundNarrow` is one function precisely because this pair is what the catalog splits
      // into two rows: the narrowing is java's implicit cast back to the left-hand type
      // (JLS 15.26.2), and it is owed wherever the assignment happens — the position only
      // decides whether the resulting value is also used.
      val narrow = Obligations.consult(JS.E(4), originOf(a))(compoundNarrow(a))
        .map(t => tpe(t))
      // JS-E17 — lvalue single evaluation (F7), expression dispatch. Same as the statement arm.
      Obligations.consult(JS.E(17), originOf(a))(Some(()))
      val st  = Tree.Assign(lhs, rhs, unitT, originOf(a), compound = Some((op, narrow)))
      Tree.Block(List(st), lhs, ty(a), originOf(a))
    case a: CtAssignment[?, ?] =>
      // Java's assignment-as-EXPRESSION needs the same coercion as the statement form. It did
      // not have it, so a conversion Java made silently was written out on one path and dropped
      // on the other — `data = (this.data = Arrays.copyOf(data, n))` kept the erased
      // `Array[Object]` the argument cast produced, in an `Array[T]` field.
      val lhs = expr(a.getAssigned)
      val rhs = a.getAssignment
      slotConsults(Option(a.getAssigned.getType).map(_ -> rhs).toList, originOf(a))
      val v   = Option(a.getAssigned.getType).map(coerce(_, rhs, expr(rhs))).getOrElse(expr(rhs))
      val st  = Tree.Assign(lhs, toDeclaredTypeParam(a.getAssigned, rhs, v), unitT, originOf(a))
      // JS-E15. This consult ALWAYS fires and that is the honest answer, not a formality: an
      // assignment reaching the EXPRESSION dispatch is by definition one whose value java
      // yields, so the difference applies at every site. Where it did not apply the answer is
      // `st` itself — a plain `Unit` assignment, which is exactly what the statement dispatch
      // emits — so the two branches are the two languages' two forms and neither is dead text.
      Obligations.consult(JS.E(15), originOf(a))(Some(lhs))
        .fold[Term](st)(v2 => Tree.Block(List(st), v2, ty(a), originOf(a)))
    case c: CtConditional[?] =>
      // Java `b ? x : null` typed as the type parameter `V`; Scala infers `x.type | Null`, which
      // won't satisfy a `V` slot. Cast a null branch to the conditional's own type so the ternary
      // stays `V`. Guarded: only when that type resolves (never emit the `?T` unresolved stub).
      val ct = ty(c)
      // JS-E05: the row is `Handled`, and this arm is HALF of it. Java COMPUTES the
      // conditional's type (JLS 15.25.2) where scala takes the lub of the branches; a NULL
      // branch is ascribed to the conditional's own type here. Every PRIMITIVE disagreement is a
      // conversion performed on the OPERAND by [[promotedBranch]], leaving nothing for the
      // emitter to ascribe. A reference conditional with no null branch needs neither.
      val ascribe = Obligations.consult(JS.E(5), originOf(c))(
        if ct != NoType && condTypeResolves(c) then Some(ct) else scala.None)
      def branch(be: CtExpression[?]): Term =
        val t      = expr(be)
        val isNull = be match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        if isNull then ascribe.fold(t)(a2 => Tree.Typed(t, tt(a2, be), a2, originOf(be)))
        else promotedBranch(c, be, t)
      Tree.If(expr(c.getCondition), branch(c.getThenExpression), branch(c.getElseExpression), ct, originOf(c))
    case ta: CtTypeAccess[?] => Tree.Literal(Constant.ClassOfC(tpe(ta.getAccessedType)), ty(e), originOf(e))
    case sw: CtSwitchExpression[?, ?] => switchExpr(sw)
    // …and the same for an EXPRESSION. The marker carries the expression's own type, so the
    // tree stays typed and every phase after this one reads the slot exactly as it would
    // have — which is the whole reason the marker is a wrapper rather than a hole.
    case other => unlowered(other, s"expression ${SpoonKinds.nameOf(other.getClass)}", ty(e))

  /** JS-E05's NUMERIC half — JLS 15.25.2's binary numeric promotion, on the OPERAND, not a cast
    * at the enclosing slot (K17): java computes a conditional's PRIMITIVE type where scala takes
    * the lub, so each operand converts here. BOTH DIRECTIONS (scala 3 dropped weak conformance,
    * probed 3.8.4); never promotes on its own, never touches a REFERENCE conditional. Operand
    * read through [[castType]]; an unresolvable type is left ALONE (§4.6). */
  private[spoon] def promotedBranch(c: CtConditional[?], be: CtExpression[?], t: Term): Term =
    val cj = c.getType
    val bj = castType(be)
    if cj == null || bj == null || !cj.isPrimitive || cj.getQualifiedName == bj.getQualifiedName then t
    else if !bj.isPrimitive then
      // a boxed operand at a primitive conditional: java UNBOXES it, then widens. Only a wrapper
      // can stand here in valid java — anything else needed a cast the source itself wrote.
      if wrapperOf.values.toSet(bj.getQualifiedName) then unbox(t, bj.getQualifiedName, cj.getSimpleName, be) else t
    else if primRank.contains(bj.getSimpleName) && primRank.contains(cj.getSimpleName) then
      // BOTH DIRECTIONS — the two primitives differ, so java converted and the port owes it
      // (JS-E06: `asInstanceOf` between statically primitive types is a conversion both ways).
      // Redundant where an expected type already reaches the branch, never WRONG.
      Tree.Typed(t, tt(tpe(cj), be), tpe(cj), originOf(be))
    else t

  /** the conditional's static type is safe to ascribe onto a null branch — a concrete type, or a
    * type parameter that actually resolves in scope (not the `?T` unresolved stub). */
  private[spoon] def condTypeResolves(c: CtConditional[?]): Boolean =
    (c.getType) match
      case null                         => false
      case tp: CtTypeParameterReference => resolveTypeParam(tp.getSimpleName).isDefined
      case _                            => true

  private[spoon] def literal(l: CtLiteral[?]): Term =
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

  private[spoon] def resolveVar(ref: CtVariableReference[?]): SymId =
    val decl = Option(ref.getDeclaration).orNull
    if decl != null && varIds.containsKey(decl) then varIds.get(decl)
    else nameIds.getOrElse(ref.getSimpleName, minter.external("?var$" + ref.getSimpleName, ref.getSimpleName))

  private[spoon] def newArray(na: CtNewArray[?])(using Obligations): Term =
    val elemT = na.getType match
      case arr: CtArrayTypeReference[?] => tpe(arr.getComponentType)
      case t                            => tpe(t)
    val inits = na.getElements.asScala.toList
    val dims  = na.getDimensionExpressions.asScala.toList
    val et    = tt(elemT, na)
    // An array INITIALISER is a slot like any other: `new Object[]{type, true}` autoboxes in
    // Java, and Scala will not box into an `Array[Object]` on its own. Coerce each element to
    // the component type, exactly as a call argument is coerced to its formal.
    val comp = na.getType match
      case arr: CtArrayTypeReference[?] => arr.getComponentType
      case _                            => null
    def elem(e: CtExpression[?]): Term =
      if comp == null then expr(e) else coerce(comp, e, expr(e))
    // an array INITIALISER is a slot list — JS-G09/G13/G14, exactly as at a call.
    slotConsults(if comp == null then Nil else inits.map(comp -> _), originOf(na))
    // JS-G15 — java FORBIDS `new T[n]` (JLS 15.10.1), so the only generic array creation
    // reaching this arm is the CAST IDIOM, `(T[]) new Object[n]`. Predicate is the idiom
    // itself — a cast on this creation whose target is an array of something generic — not
    // "the component is a type variable" (javac already made that unreachable). Handled by
    // JS-G13's `coerce`'s `arrayCov` clause.
    val idiomCasts = na.getTypeCasts.asScala.toList
    Obligations.consult(JS.G(15), originOf(na))(Option.when(
      idiomCasts.collect { case a: CtArrayTypeReference[?] => a.getComponentType }
        .exists(c => c != null && (c.isInstanceOf[CtTypeParameterReference] || isGenericUse(c))))(()))
    if inits.nonEmpty || dims.isEmpty then Tree.NewArray(et, Nil, Some(inits.map(elem)), ty(na), originOf(na))
    else Tree.NewArray(et, dims.map(expr), None, ty(na), originOf(na))

  private[spoon] def lambda(l: CtLambda[?]): Term =
    val pvs = l.getParameters.asScala.toList.map { p =>
      val pt = tpe(p.getType)
      Tree.ValDef(defineLocal(p, pt), tt(pt, p), None, originOf(p))
    }
    val body =
      if l.getExpression != null then nullToSamResult(l, expr(l.getExpression))
      else if l.getBody != null then blockTerm(l.getBody)
      else unsupported(l, "lambda without body")
    // …and the SAM METHOD's result type where the class file states one (`ENGINE-LIMITS.md` I9).
    // Without it the emitter cannot name the nested `def` that restores java's
    // `return`-leaves-the-LAMBDA, and what it leaves instead is not a compile error but a scala
    // NON-LOCAL RETURN from the enclosing method — valid, green, and something else (M6).
    Tree.Lambda(pvs, body, ty(l), originOf(l), resultTpt = samResultTpt(l))

  /** [[nullToTypeParam]]'s rule at the ONE expression position with no formal: an EXPRESSION-
    * bodied lambda takes its body type from the SAM's RESULT (JLS 15.27.3), so where that
    * result is the target's own type variable, `Null` needs an explicit cast. Variable resolved
    * through the TARGET's own instantiation ([[typeArgSubst]], G12's rule at a lambda). */
  private[spoon] def nullToSamResult(l: CtLambda[?], t: Term): Term =
    val isNull = l.getExpression match { case lit: CtLiteral[?] => lit.getValue == null; case _ => false }
    if !isNull then t
    else samAbstracts(l.getType) match
      case one :: Nil =>
        (Option(one.getType), Option(one.getDeclaringType).map(_.getQualifiedName)) match
          case (Some(tp: CtTypeParameterReference), Some(owner)) =>
            actualFor(l.getType, owner, tp.getSimpleName, 8) match
              case Some(tv: CtTypeParameterReference) if tpNameableHere(tv) =>
                Tree.Typed(t, tt(tpe(tv), l), tpe(tv), originOf(l))
              case _ => t
          case _ => t
      case _ => t

  /** What does reference `at` say the variable `tv`, DECLARED BY `owner`, is? Composed one edge
    * at a time through the `extends` chain (`Maker<T> extends Fn<String,V>` resolves `Fn.R` via
    * `V := T` then `R := V`) — replaces Spoon's `TypeAdaptor`, which under `noClasspath` hands
    * back the interface's own variable un-adapted. Fuel-bounded; `None` where a declaration
    * cannot be read, caught by the caller's bare-`null` fallback (§4.6). */
  private[spoon] def actualFor(at: CtTypeReference[?], owner: String, tv: String,
                        fuel: Int): Option[CtTypeReference[?]] =
    def walk(here: CtTypeReference[?], subst: Map[String, CtTypeReference[?]], f: Int): Option[CtTypeReference[?]] =
      if f <= 0 || here == null then scala.None
      else if here.getQualifiedName == owner then subst.get(tv)
      else
        val decl = typeDeclarationOf(here)
        val supers = decl.toList.flatMap { d =>
          (d.getSuperInterfaces.asScala.toList) ++
            (Option(d.getSuperclass).toList)
        }
        supers.iterator.map { s =>
          // `s`'s actuals are written in `here`'s scope, so they are read THROUGH `subst`
          // before they become `s`'s own frame.
          val formals = typeDeclarationOf(s).map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
          val actuals = (s.getActualTypeArguments.asScala.toList)
            .map { case tp: CtTypeParameterReference => subst.getOrElse(tp.getSimpleName, tp); case o => o }
          val frame = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap else Map.empty
          walk(s, frame, f - 1)
        }.collectFirst { case Some(r) => r }
    walk(at, typeArgSubst(at), fuel)

  private[spoon] def methodRef(mr: CtExecutableReferenceExpression[?, ?])(using Obligations): Term =
    val mid = methodSym(mr.getExecutable)
    val qual: Either[TypeTree, Term] = mr.getTarget match
      case ta: CtTypeAccess[?] => Left(tt(tpe(ta.getAccessedType), mr))
      case t                   => Right(expr(t))
    // JS-G43 — five forms share one java syntax and each is a different scala lambda, so the
    // FRONTEND half of the row is exactly this: carry the reference as its own node rather than
    // guessing a shape here. Always fires, and that is the honest answer — every method
    // reference is one of the five and every one of them needs the discrimination the emitter
    // then performs, off the [[Referent]] this reads.
    Obligations.consult(JS.G(43), originOf(mr))(Some(()))
    Tree.MethodRef(qual, mid, ty(mr), originOf(mr), referentOf(mr.getExecutable))

  /** the referenced executable's `static` modifier and its declared ARITY — read HERE since
    * neither survives to the symbol for an EXTERNAL member ([[Tree.MethodRef.referent]]). The
    * DECLARATION answers where the parse resolved one (even a class-file shadow); the REFERENCE
    * is the fallback and its arity is exact even under a lenient parse. `isStatic` on a
    * reference with no declaration is the one value here that can be a guess. */
  private[spoon] def referentOf(ex: CtExecutableReference[?]): Referent =
    val decl = Option(ex.getExecutableDeclaration)
    val stat = decl match
      case Some(d) => execFlags(d).isStatic
      case None    => ex.isStatic
    // the ARITY is read for BOTH cases, not only the unbound one: a NILARY static reference is
    // the one qualified name scala will not eta-expand, so the emitter needs the number there
    // too (see [[Referent]], `ENGINE-LIMITS.md` G32).
    val n = decl.map(_.getParameters.asScala.size)
      .getOrElse(ex.getParameters.asScala.size)
    if stat then Referent.Static(n) else Referent.Instance(n)

  private[spoon] def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?], at: CtExpression[?])
                         (using Obligations): Term =
    // JS-C02 / JS-C05 — the same two facts as at an invocation, arriving at a FIELD. A static
    // field reached through a type name is inherited through `extends` AND `implements` (JLS
    // 9.3); a static nested CONSTANT reached through a subclass's name is the same question with
    // a nested path. Both consulted here, read off the reference: `getDeclaringType` IS the
    // declarer, so no BFS is re-run to answer a diagnostic.
    val staticRecv = target.isInstanceOf[CtTypeAccess[?]]
    Obligations.consult(JS.C(2), originOf(at))(Option.when(staticRecv && (target match
      case ta: CtTypeAccess[?] =>
        // Through `declaringStaticType` — this row's own evidence symbol — not through the
        // reference's `getDeclaringType`/`getFieldDeclaration`. For `C.X` where `X` is `K`'s
        // constant, Spoon's reference reads back the SOURCE-WRITTEN type and the declaration
        // does not resolve, so both answered "not inherited" at exactly the interface-constant
        // shape this row is named for. Costs one extra walk, only at a STATIC field read.
        Option(ta.getAccessedType).exists(a =>
          declaringStaticType(a, ref.getSimpleName).exists(_.getQualifiedName != a.getQualifiedName))
      case _ => false))(()))
    Obligations.consult(JS.C(5), originOf(at))(Option.when(staticRecv)(()))
    if ref.getSimpleName == "class" then
      // `Foo.class` → `classOf[Foo]`: the argument is the ACCESSED type (`Foo`), not the type
      // of the `.class` expression (`java.lang.Class[Foo]`, which is what `ty(at)` gives).
      val accessed = target match { case ta: CtTypeAccess[?] => tpe(ta.getAccessedType); case _ => ty(at) }
      Tree.Literal(Constant.ClassOfC(accessed), ty(at), originOf(at))
    else if ref.getSimpleName == "length" && Option(ref.getDeclaringType).exists(_.isInstanceOf[CtArrayTypeReference[?]]) then
      Tree.ArrayLength(expr(target), ty(at), originOf(at))
    else
      val fid = fieldSym(ref)
      target match
        case ta: CtTypeAccess[?]                 => staticFieldAccess(ta, ref, fid, at) // static (re-qualify if inherited)
        // fields: `super.f`, an outer `Outer.this.f`, and implicit `f` all resolve as a BARE
        // name in Scala (inherited or enclosing). Only an OWN `this.f` needs qualifying.
        case _: CtSuperAccess[?]                 => Tree.Ident(fid, ty(at), originOf(at))
        case null                                => Tree.Ident(fid, ty(at), originOf(at))
        case ta: CtThisAccess[?] if !isOwnThis(ta) =>
          // as for calls: `Outer.this.f` is written precisely when an inherited/own `f` would
          // otherwise win, so the qualification is load-bearing. Bare only when the enclosing
          // instance is not really reachable (a static-nested boundary, or an inherited owner).
          outerThis(ta).map(q => Tree.Select(q, fid, ty(at), originOf(at)))
            .getOrElse(Tree.Ident(fid, ty(at), originOf(at)))
        case _: CtThisAccess[?]                  => Tree.Select(thisTerm(at), fid, ty(at), originOf(at))
        case other =>
          // wildcard/raw receiver whose field type depends on its type vars → read through the
          // ERASED view, exactly as for a call (`erasedReceiverView`). The READ's type moves with
          // the receiver: `data.type` off `AssetData[Object]` is `Class[Object]`, not the
          // un-erased `Class[T]`. Carrying Spoon's type made the TIR disagree with the emitted
          // Scala, and every later rule consulting `t.tpe` silently found nothing to convert.
          erasedFieldReceiver(ref, other) match
            case Some((et, ft)) =>
              val recv = Tree.Typed(expr(other), tt(et, other), et, originOf(other))
              Tree.Select(recv, fid, ft, originOf(at))
            case None => Tree.Select(expr(other), fid, ty(at), originOf(at))

  /** A field read/written through a WILDCARD/RAW receiver: `assetDesc.type` off
    * `AssetDescriptor[?]` yields a fresh CAPTURE that unifies with nothing downstream. Java
    * reads such a field at the ERASED type; emit that view. Returns BOTH the receiver's erased
    * type and the field's type through it, since a `Tree.Select` carrying Spoon's un-erased
    * field type over an erased receiver is a TIR node the emitted Scala does not have. */
  private[spoon] def erasedFieldReceiver(ref: CtFieldReference[?], target: CtExpression[?]): Option[(TypeRepr, TypeRepr)] =
    val rt = castType(target)
    if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
       rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
    else
      val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
      val actuals = rt.getActualTypeArguments.asScala.toList
      // a BOUNDED wildcard (`IntMap<? extends V> map`) still gives a usable capture — `map.zeroValue`
      // conforms to `V` — so only a raw use or an UNBOUNDED `?` needs the erased view here.
      val useless = (a: CtTypeReference[?]) => a match
        case w: CtWildcardReference => Option(w.getBoundingType).forall(_.getQualifiedName == "java.lang.Object")
        case _                      => false
      val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(useless))
      val names   = formals.map(_.getSimpleName).toSet
      val declTpe = fieldDeclOf(ref).map(_.getType).filter(_ != null)
      val depends = declTpe.exists(mentionsTypeVarFilled(_, names))
      if unknown && depends then
        // same F-bound treatment as `erasedReceiverView`: an F-bounded class has no erased
        // image, so fill from the enclosing scope's own variables and leave what cannot be
        // named as `?`. Without this a FIELD access through such a receiver still emitted
        // `Node[Node[?, Object, Actor], Object, Actor]`, which fails its own bound.
        def isFB(f: CtTypeParameter): Boolean =
          Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
        val anyFB = formals.exists(isFB)
        val erasedArgs = formals.map { f =>
          val nm = if inStatic || !anyFB then scala.None else accessibleTp(f.getSimpleName)
          // on the name-filled path a formal we cannot name must be `?`, not its erasure: the
          // F-bound names its SIBLINGS, so pinning `A` to `Actor` while `N`'s bound still reads
          // `Node[N, V, A]` leaves `N` failing its own bound.
          nm.map(id => TypeRef(NoPrefix, id)).getOrElse {
            if anyFB || isFB(f) then TypeBounds(NoType, NoType) else erasureOfFormal(f, Set.empty, 2)
          }
        }
        val subst      = formals.map(_.getSimpleName).zip(erasedArgs).toMap
        Some((AppliedType(TypeRef(NoPrefix, typeSym(rt)), erasedArgs),
              declTpe.map(erasedFormal(_, subst)).getOrElse(objectT)))
      else None

  /** A Java static field read through a SUBCLASS (`Rotational3D.TMP_V3`, where `TMP_V3` is declared
    * in an ancestor `DynamicsModifier`) — Scala companion objects don't inherit statics, so resolve
    * the field to its real DECLARING type and qualify by that (`DynamicsModifier.TMP_V3`). Falls back
    * to the written qualifier when the declaring type can't be located. */
  private[spoon] def staticFieldAccess(ta: CtTypeAccess[?], ref: CtFieldReference[?], fid: SymId, at: CtExpression[?]): Term =
    val name  = ref.getSimpleName
    val ownerT = declaringStaticType(ta.getAccessedType, name)
    ownerT match
      case Some(t) if t.getQualifiedName != ta.getAccessedType.getQualifiedName =>
        val ownerId = minter.external(t.getQualifiedName, simpleName(t.getQualifiedName))
        val fid2    = externalMember(ownerId, name, name)
        Tree.Select(Tree.Ident(ownerId, TypeRef(NoPrefix, ownerId), originOf(at)), fid2, ty(at), originOf(at))
      case _ => Tree.Select(typeTerm(ta, at), fid, ty(at), originOf(at))

  /** the type that DECLARES a static field `name` (degrades to `None` on shadow/unresolved
    * types). Walks the WHOLE inheritance closure (superclass AND superinterfaces, CLAUDE.md
    * §1a — a java interface constant is inherited through `implements` too), breadth-first with
    * the class edge FIRST (java's own shadowing precedence). */
  private[spoon] def declaringStaticType(accessed: CtTypeReference[?], name: String): Option[CtType[?]] =
    val seen  = collection.mutable.Set[String]()
    val queue = collection.mutable.Queue[CtType[?]]()
    def decl(r: CtTypeReference[?]): CtType[?] =
      typeDeclarationOf(r).orNull
    Option(decl(accessed)).foreach(queue.enqueue)
    while queue.nonEmpty do
      val t = queue.dequeue()
      if t != null && seen.add(t.getQualifiedName) then
        if (t.getFields.asScala.exists(_.getSimpleName == name)) then return Some(t)
        val parents =
          Option(t.getSuperclass).toList ++ t.getSuperInterfaces.asScala.toList
        parents.map(decl).filter(_ != null).foreach(queue.enqueue)
    None

  private[spoon] def fieldSym(ref: CtFieldReference[?]): SymId =
    val ownerQ = fieldDeclOf(ref).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)
      .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
      .getOrElse("java.lang.Object")
    val ownerId = minter.external(ownerQ, simpleName(ownerQ))
    externalMember(ownerId, ref.getSimpleName, ref.getSimpleName, info = externalFieldType(ref))

  /** the DECLARED type of an EXTERNAL field, as a class file states it — [[externalSignature]]'s
    * fact for a field (the seam a `Select` node makes is invisible to anything keyed on
    * `Tree.Apply`, ENGINE-LIMITS K15). Rendered SCOPE-FREE through [[externalSlot]]. Only for a
    * SHADOW declaration; a program-declared field gets its real type from `fieldDef`. */
  private[spoon] def externalFieldType(ref: CtFieldReference[?]): TypeRepr =
    fieldDeclOf(ref) match
      case scala.None => NoType // no declaration to read — not evidence of anything
      case Some(fd)   =>
        val shadow = Option(fd.getParent(classOf[CtType[?]])).forall(_.isShadow)
        if !shadow then NoType else externalSlot(fd.getType)

  /** Java's WILDCARD/RAW-receiver calls: scala gives every member access a fresh CAPTURE, so a
    * value off one receiver never conforms to another's formal. Java's own view is ERASED,
    * performed unchecked — cast the RECEIVER to its erased instantiation and each dependent
    * argument to that formal's erasure (same erasure rules, so they agree). Gated to calls that
    * genuinely DEPEND on the receiver's type variables. */
  private[spoon] def erasedReceiverView(inv: CtInvocation[?]): Option[(TypeRepr, Map[String, TypeRepr], Map[String, TypeRepr])] =
    val ex = inv.getExecutable
    if ex.isConstructor then None
    else inv.getTarget match
      case null => None
      case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => None
      case t =>
        // an explicit CAST is what fixes the static type javac dispatched on
        // (`((AsynchronousAssetLoader) loader).unloadAsync(…)`) — Spoon keeps it beside the
        // expression, whose own type is still the field's, so the outermost cast wins.
        val rt = castType(t)
        // A FIELD read reports the reference's erased view, not the declaration's: `node.parent`
        // of `public N parent` types as the RAW `Node` under noClasspath, which reads as "the
        // arguments are unknown" and triggers an erasure the code never needed — Java's own type
        // for it is simply `N`. The declaration is the honest source, exactly as it is for the
        // raw fill (`atDeclScope`), so consult it and decline when it names a type variable.
        val declaredVar = t match
          case fa: CtFieldAccess[?] =>
            fieldDeclOf(fa.getVariable).map(_.getType)
                  .exists(_.isInstanceOf[CtTypeParameterReference])
          case _ => false
        if declaredVar then None
        else if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]]
           || rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
        else
          val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
          val actuals = rt.getActualTypeArguments.asScala.toList
          val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(_.isInstanceOf[CtWildcardReference]))
          val names   = formals.map(_.getSimpleName).toSet
          val depends =
            execDeclOf(ex)
                  .map(_.getParameters.asScala.toList.map(_.getType))
                  .exists(_.exists(p => p != null && mentionsTypeVarFilled(p, names)))
          // Only an F-BOUNDED class needs this: there the erasure has no Scala image at all,
          // so the name-directed fill is the only expressible reading. Everywhere else the
          // erasure is load-bearing and preferring in-scope names by NAME measured 2 -> 9.
          def isFBounded(f: CtTypeParameter): Boolean =
            Option(f.getSuperclass).exists(b => mentionsTypeVarFilled(b, Set(f.getSimpleName)))
          val anyFBounded = formals.exists(isFBounded)
          // THE VIEW IS DECIDED PER POSITION — `unknown` asked of the WHOLE list is wrong for a
          // MIXED one (a position java left WRITTEN must be carried, not erased). Carried only
          // where the argument mentions NO TYPE VARIABLE (the three erasure readings must AGREE,
          // ENGINE-LIMITS G21). F-BOUNDED classes excluded whole (arguments discharge each
          // other's bounds).
          def writtenAt(i: Int): Option[TypeRepr] =
            if anyFBounded then scala.None
            else actuals.lift(i).flatMap { a =>
              TypeShape.of(a) match
                // exactly what the source left UNKNOWN — this is `unknown`'s own criterion, asked
                // where java asks it.
                case TypeShape.Wildcard(_, _, _) => scala.None
                case _ if mentionsAnyTypeVar(a)  => scala.None
                case _                           => Some(tpe(a))
            }
          if unknown && depends then
            // An F-bounded formal erases to a WILDCARD here too, for the same reason it does
            // inside `erasedType`: `Node[Node[?, Object, Actor], Object, Actor]` still fails
            // `N <: Node[N,V,A]`, because `Node` is invariant and the argument would have to be
            // the very type being written. Only `?` discharges the bound.
            val namedOf = collection.mutable.Map[String, TypeRepr]()
            val args  = formals.zipWithIndex.map { (f, i) => writtenAt(i).getOrElse {
              // prefer the NAME-DIRECTED fill over the erasure — an F-bound's erasure has no
              // finite Scala image, but the enclosing scope's own variables discharge the bound
              // by construction (same rule `nameFilledArgs` already applies to types)
              val named = if inStatic || !anyFBounded then scala.None else accessibleTp(f.getSimpleName)
              named.map { id => val r = TypeRef(NoPrefix, id); namedOf(f.getSimpleName) = r; r }.getOrElse {
                if anyFBounded || isFBounded(f) then TypeBounds(NoType, NoType)
                else erasureOfFormal(f, Set.empty, 2)
              }
            } }
            val subst = formals.map(_.getSimpleName).zip(args).toMap
            Some((AppliedType(TypeRef(NoPrefix, typeSym(rt)), args), subst, namedOf.toMap))
          else None

  /** `(N) this` — the SELF-TYPE conversion at a raw call. Java accepted `this` at a raw
    * receiver's `N` formal only because the receiver is raw — libGDX writes `(N) this` itself
    * where it isn't. Restricted to `this`: a general "cast any argument" rule measured 1 -> 11. */
  private[spoon] def selfTypeArgs(
      ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term],
      nm: Map[String, TypeRepr],
  ): List[Term] =
    if nm.isEmpty then args
    else
      val ps = execDeclOf(ex).map(_.getParameters.asScala.toList.map(_.getType))
      ps match
        case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
          args.zipWithIndex.map { (t, i) =>
            (l(i), argEs(i)) match
              case (tv: CtTypeParameterReference, _: CtThisAccess[?])
                  if nm.contains(tv.getSimpleName) && nm(tv.getSimpleName) != t.tpe =>
                val ct = nm(tv.getSimpleName)
                Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
              case _ => t
          }
        case _ => args

  /** cast each argument whose DECLARED formal mentions a receiver type variable to that
    * formal's erasure, matching the erased receiver the call is now made through. */
  private[spoon] def eraseDependentArgs(
      ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term], subst: Map[String, TypeRepr],
      named: Map[String, TypeRepr] = Map.empty,
  ): List[Term] =
    val names = subst.keySet
    val ps = execDeclOf(ex).map(_.getParameters.asScala.toList.map(_.getType))
    ps match
      case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
        args.zipWithIndex.map { (t, i) =>
          val f = l(i)
          if f == null || !mentionsTypeVarFilled(f, names) then t
          else
            val et = erasedFormal(f, subst)
            // a BARE `?` is not a type one can cast to (`asInstanceOf[?]` is a syntax error).
            // It arises when the formal is exactly an F-bounded variable, whose erasure is now
            // the wildcard — and there the cast has nothing to say anyway: the receiver was
            // already erased to `Node[?, …]`, so the argument's own type is what must match it.
            if et.isInstanceOf[TypeBounds] then t
            else Tree.Typed(t, tt(et, argEs(i)), et, t.origin)
        }
      case _ => args

  /** Java's UNCHECKED conversion at an ARGUMENT, when the RECEIVER's instantiation is KNOWN —
    * the complement of [[erasedReceiverView]] (whose receiver is UNKNOWN, mutually exclusive).
    * Narrow: only a formal mentioning a receiver type variable, only an argument our raw fill
    * wildcarded, only when the substituted formal is fully nameable here. */
  private[spoon] def knownReceiverArgs(inv: CtInvocation[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
    val rt = inv.getTarget match
      case null => null
      case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => null
      case t    => castType(t)
    if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
       rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then args
    else
      val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
      val actuals = rt.getActualTypeArguments.asScala.toList
      // fully KNOWN instantiation (same arity, every variable nameable). A WILDCARD actual is
      // admitted too — it cannot drive the cast but makes a NARROWER argument illegal, and java
      // converts silently there (see the per-argument guard below).
      val known = formals.nonEmpty && actuals.sizeIs == formals.size && actuals.forall(tpResolvable)
      val ps = execDeclOf(inv.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
      (known, ps) match
        case (true, Some(l)) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
          // a FIELD receiver's arguments must be rendered as the FIELD's declaration rendered
          // them, not re-filled from the enclosing method's own type parameters
          val fieldRecv = inv.getTarget.isInstanceOf[CtFieldAccess[?]]
          val subst = formals.map(_.getSimpleName)
            .zip(if fieldRecv then atDeclScope(actuals.map(tpe)) else actuals.map(tpe)).toMap
          val rawElement = actuals.exists(a => isRawGenericUse(a))
          args.zipWithIndex.map { (t, i) =>
            val f = l(i)
            if f == null || !mentionsTypeVarBounded(f, subst.keySet) then t
            else substFormal(f, subst) match
              // fires on any of: the ARGUMENT was wildcarded by our raw fill (`hasWildcard`); the
              // SLOT is wildcarded and the argument more precise (`uncheckedFrom(ct,t.tpe)`); the
              // receiver's own type argument is a RAW use (`rawElement`); the ARGUMENT is an
              // Object-parameterised view via an ERASED RECEIVER (`uncheckedFrom(t.tpe,ct)`) —
              // all java's unchecked conversion, narrow by construction (same constructor/arity).
              case Some(ct) if ct != t.tpe && !ct.isInstanceOf[TypeBounds] &&
                               (hasWildcard(t.tpe) || uncheckedFrom(ct, t.tpe) ||
                                uncheckedFrom(t.tpe, ct) || rawElement) =>
                Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
              case _ => t
          }
        case _ => args

  /** A call THROUGH A TYPE VARIABLE whose bound is a RAW generic (`N extends Node`): java sees
    * the callee's members ERASED, so it accepts arguments the un-erased Scala signature rejects.
    * Cast each argument whose DECLARED formal is a type parameter to that parameter as resolved
    * HERE. Gated on the receiver being a type variable, so an ordinary generic call is untouched. */
  private[spoon] def typeVarReceiverArgs(inv: CtInvocation[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
    val recvIsTypeVar = inv.getTarget match
      case null => false
      case t    => (t.getType) match
        case tv: CtTypeParameterReference =>
          // only a RAW-generic bound erases the members; a properly applied bound does not.
          val d = typeParamDeclOf(tv)
          d.flatMap(x => Option(x.getSuperclass)).exists(isRawGenericUse)
        case _ => false
    if !recvIsTypeVar then args
    else
      val ps = execDeclOf(inv.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
      ps match
        case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
          args.zipWithIndex.map { (t, i) =>
            l(i) match
              case tp: CtTypeParameterReference => toTypeParam(tp, argEs(i), t)
              case _                            => t
          }
        case _ => args

  private[spoon] def invocation(inv: CtInvocation[?])(using Obligations): Term =
    val ex   = inv.getExecutable
    val mid  = methodSym(ex)
    // JS-C01/C02: a java `static` is INHERITED, a scala companion inherits nothing — re-point
    // at the DECLARING type. Read off the reference, not by re-running the BFS.
    val staticRecv = inv.getTarget.isInstanceOf[CtTypeAccess[?]]
    Obligations.consult(JS.C(1), originOf(inv))(Option.when(staticRecv)(()))
    Obligations.consult(JS.C(2), originOf(inv))(Option.when(staticRecv && (inv.getTarget match
      case ta: CtTypeAccess[?] =>
        val written = Option(ta.getAccessedType).map(_.getQualifiedName)
        val decl    = Option(ex.getDeclaringType).map(_.getQualifiedName)
        written.isDefined && decl.isDefined && written != decl
      case _ => false))(()))
    val argEs = inv.getArguments.asScala.toList
    val erasedRecv = erasedReceiverView(inv)
    val recvSubst  = receiverTypeArgs(inv)
    // JS-G22 — a raw member access through an ERASED RECEIVER types the CALL and not only the
    // receiver: java inserted a checkcast on the way back out, and `erasedRecvResult` writes it.
    // Read off the view this arm has just computed, never re-derived (§4.56).
    Obligations.consult(JS.G(22), originOf(inv))(Option.when(erasedRecv.isDefined)(()))
    // JS-G29/G30: a variable some FORMAL mentions infers the same in both languages (G29); one
    // no formal mentions resolves to its BOUND in java and `Nothing` in scala (G30)
    val calleeTpNames = execDeclOf(ex)
                              .collect { case m: CtMethod[?] => m.getFormalCtTypeParameters.asScala.toList }
                              .getOrElse(Nil).map(_.getSimpleName).toSet
    val constrained = calleeTpNames.nonEmpty &&
        Option(ex.getExecutableDeclaration).exists(
          _.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, calleeTpNames)))
    Obligations.consult(JS.G(29), originOf(inv))(Option.when(constrained)(()))
    Obligations.consult(JS.G(30), originOf(inv))(Option.when(calleeTpNames.nonEmpty && !constrained)(()))
    val args0 = erasedRecv match
      // A NAME-FILLED receiver needs no argument erasure at all. The callee's formals are then
      // expressed in the caller's OWN type variables (`addToTree(Tree<N,V>)` against a receiver
      // read as `Node[N, V, Actor]`), and the values at hand already have those types — `this`
      // IS a `Tree[N, V]`. Erasing them re-introduced the mismatch the name-fill just removed.
      case Some((_, subst, named)) if named.isEmpty =>
        eraseDependentArgs(ex, argEs, coerceArgs(ex, argEs, originOf(inv), recvSubst), subst)
      case Some((_, _, nm)) => selfTypeArgs(ex, argEs, coerceArgs(ex, argEs, originOf(inv), recvSubst), nm)
      case None             => coerceArgs(ex, argEs, originOf(inv), recvSubst)
    val o    = originOf(inv)
    // JS-G31. Every arm above may cast an argument to the formal it read; a POLY EXPRESSION is
    // the one argument that has no type to cast FROM, so the call answers for it here, once,
    // after all of them have run. See `polyExpression` for the probe this rests on.
    val args = polyArgsAscribed(ex, argEs,
      polyArgsUncast(argEs, typeVarReceiverArgs(inv, argEs, knownReceiverArgs(inv, argEs, args0)), o))
    val fun: Term =
      if ex.isConstructor then
        // super()/this() delegation — target class ≠ enclosing ⇒ super (Spoon often nulls the target).
        val superCtor = inv.getTarget.isInstanceOf[CtSuperAccess[?]] ||
          Option(ex.getDeclaringType).map(_.getQualifiedName).exists(_ != minter.fullNameOf(classId))
        Tree.Select(if superCtor then superTerm(inv) else thisTerm(inv), mid, NoType, o)
      else inv.getTarget match
        case _: CtSuperAccess[?]  => Tree.Select(superTerm(inv), mid, NoType, o)
        case ta: CtTypeAccess[?]  => Tree.Select(staticCallQualifier(ta, mid, inv), mid, NoType, o)
        // implicit (no target): a BARE reference resolves an own OR an ENCLOSING member
        // (Scala inner classes see the outer's members by simple name). Explicit `this.m`
        // stays qualified — it's used precisely to defeat param/local shadowing.
        case null                                  =>
          shadowedImplicitCall(inv, mid, o).getOrElse(Tree.Ident(mid, NoType, o))
        // `Outer.this.m(…)`. Java resolves a simple name against the INNERMOST type that has
        // such a member, so `CharArray.this.append(cbuf)` inside `CharArrayWriter extends Writer`
        // is qualified precisely because the inherited `Writer.append` would otherwise win.
        // Emitted bare, Scala calls that ambiguous. Keep Java's qualification.
        case ta: CtThisAccess[?] if !isOwnThis(ta) =>
          shadowedImplicitCall(inv, mid, o)
            .orElse(outerThis(ta).map(q => Tree.Select(q, mid, NoType, o)))
            .getOrElse(Tree.Ident(mid, NoType, o))
        case _: CtThisAccess[?]                    => Tree.Select(thisTerm(inv), mid, NoType, o)
        case t =>
          val recv = expr(t)
          // wildcard/raw receiver whose callee depends on its type vars → call through the
          // ERASED view (Java's own), so the formals stop being per-access captures.
          val recv2 = erasedRecv match
            case Some((et, _, _)) => Tree.Typed(recv, tt(et, t), et, originOf(t))
            case None             => recv
          Tree.Select(recv2, mid, NoType, o)
    val app = ascribeUnconstrainedResult(inv,
      Tree.Apply(pinTypeArgs(fun, inv, o), args, mid, erasedResult(args, ty(inv)), o), o)
    erasedRecvResult(inv, erasedRecv, app)

  /** The downcast an ERASED RECEIVER's result needs — java pays for calling through the erased
    * view ([[erasedReceiverView]]) with an implicit downcast on the result, which this writes
    * down. Gated on the un-erased result being nameable HERE and actually different. */
  private[spoon] def erasedRecvResult(
      inv: CtInvocation[?], recv: Option[(TypeRepr, Map[String, TypeRepr], Map[String, TypeRepr])], app: Term,
  ): Term = recv match
    case None => app
    case Some((_, subst, _)) =>
      val declRet = execDeclOf(inv.getExecutable)
                          .collect { case m: CtMethod[?] => m.getType }
      // the un-erased reading comes from the receiver's DECLARED arguments, not Spoon's type
      // for the call — a wildcard receiver's CAPTURE has no scala name
      val declSubst: Map[String, TypeRepr] =
        val t  = inv.getTarget
        val rt = castType(t)
        val fs = Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
        val as = rt.getActualTypeArguments.asScala.toList
        if fs.sizeIs != as.size then Map.empty
        else fs.map(_.getSimpleName).zip(as.map {
          case w: CtWildcardReference => Option(w.getBoundingType).filter(_ => w.isUpper).orNull
          case a                      => a
        }).collect { case (n, a) if a != null && tpResolvable(a) => n -> tpe(a) }.toMap
      // a RAW declared result through an ERASED receiver is where the node's type and the
      // emitted scala part company (ENGINE-LIMITS §0) — re-TYPE the node, emit nothing
      def retyped(t: Term, want: Option[TypeRepr]): Term = (t, want) match
        case (a: Tree.Apply, Some(w)) if w != a.tpe => a.copy(tpe = w)
        case _                                      => t
      val rawErasedResult = declRet.filter(d => d != null && !d.isPrimitive && isRawGenericUse(d)).flatMap { d =>
        val arity = formalArity(d)
        Option.when(arity > 0)(AppliedType(TypeRef(NoPrefix, typeSym(d)), List.fill(arity)(objectT)))
      }
      declRet match
        case Some(d) if d != null && !d.isPrimitive && mentionsTypeVarFilled(d, subst.keySet) =>
          substFormal(d, declSubst) match
            case Some(ct) if ct != app.tpe && ct != NoType && !hasWildcard(ct) =>
              Tree.Typed(app, tt(ct, inv), ct, originOf(inv))
            case _ => retyped(app, rawErasedResult)
        case _ => retyped(app, rawErasedResult)

  /** The result type an ERASED ARGUMENT drags with it — recording Spoon's un-erased type would
    * make the TIR assert what the emitted scala does not have. Only the erasure WE introduced
    * is modelled, decided from the EMITTED argument (a JDK shadow's formals are unreliable). */
  private[spoon] def erasedResult(args: List[Term], declared: TypeRepr): TypeRepr =
    val erasedArrayArg = args.exists { case Tree.Typed(_, _, at, _) => at == arrayOfObject; case _ => false }
    declared match
      // Decided from the EMITTED argument and Spoon's result type, never from the callee's
      // declaration: `java.util.Arrays` is a shadow under noClasspath and often carries no
      // return type at all, so a declaration-driven rule silently does nothing here.
      case AppliedType(_, List(a)) if erasedArrayArg && isScalaArrayType(declared) && isTypeParamRef(a) =>
        arrayOfObject
      case _ => declared

  /** is this rendered type a reference to a type PARAMETER? (they are minted `<owner>$$<name>`) */
  private[spoon] def isTypeParamRef(t: TypeRepr): Boolean = t match
    case TypeRef(_, s) => minter.fullNameOf(s).contains("$$")
    case _             => false

  private[spoon] lazy val arrayOfObject: TypeRepr =
    AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(objectT))

  /** Java keeps SEPARATE namespaces for variables and methods; Scala does not. `boolean delete =
    * …; cursor = delete(false);` is legal Java — the call still reaches the METHOD — but in
    * Scala the local hides it. Qualify such an implicit-target call with the instance whose
    * class provides the method, exactly what Java resolved. Only for INSTANCE methods in a
    * non-static context. */
  private[spoon] def shadowedImplicitCall(inv: CtInvocation[?], mid: SymId, o: Origin): Option[Term] =
    val name = inv.getExecutable.getSimpleName
    val exec = inv.getParent(classOf[CtExecutable[?]])
    val shadowed = !inStatic && exec != null &&
      exec.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtVariable[?]])).asScala
        .exists(v => !v.isInstanceOf[CtField[?]] && v.getSimpleName == name)
    if !shadowed then None
    else
      // innermost enclosing class that PROVIDES the method (own or inherited) — that is the
      // `this` Java picked.
      var t: CtType[?]      = inv.getParent(classOf[CtType[?]])
      var res: Option[Term] = None
      while t != null && res.isEmpty do
        val provides =
          t.getAllMethods.asScala.exists(m => m.getSimpleName == name && !m.hasModifier(ModifierKind.STATIC))
        if provides then
          val id = minter.external(t.getQualifiedName, t.getSimpleName)
          res = Some(Tree.Select(Tree.This(id, TypeRef(NoPrefix, id), o), mid, NoType, o))
        else t = t.getDeclaringType
      res

  /** Java resolves a generic call's type arguments (often by inference); Scala re-infers from
    * the EXPECTED type, which can pick a different `T` (E007). Pin the Java-resolved arguments
    * as an explicit `m[Targs](...)` so inference matches Java and unboxing conversions apply at
    * the result. Conservative: only when every argument is a fully concrete class type. */
  private[spoon] def pinTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
    if inv.getExecutable.isConstructor then return fun
    val formals = Option(inv.getExecutable.getExecutableDeclaration).collect {
      case m: CtMethod[?] => m.getFormalCtTypeParameters.size
    }.getOrElse(0)
    val actuals = inv.getActualTypeArguments.asScala.toList
    // Restricted to boxed-primitive wrappers: that is the whole beneficial case — a `Class<T>: T`
    // call whose `T` Scala would mis-infer to the primitive (demanding `Class[Int]`) when Java
    // bound it to the wrapper (`Class[Integer]`). Pinning `T=Integer` fixes it and lets
    // `Predef.Integer2int` unbox the result. Pinning other kinds (raw generics → `Array[?]`,
    // path-dependent types) only disturbs overload resolution, so leave those to free inference.
    if formals > 0 && actuals.nonEmpty && actuals.sizeIs == formals && actuals.forall(isBoxedWrapper)
      && !inv.getArguments.asScala.exists(isPrimitiveClassLiteral)
    then Tree.TypeApply(fun, actuals.map(a => tt(tpe(a), inv)), NoType, o)
    else pinUnconstrainedTypeArgs(fun, inv, o)

  /** A method TYPE PARAMETER that appears in NO FORMAL, at a call with no target type either
    * (ENGINE-LIMITS G22). Java instantiates it at its BOUND; scala instantiates it at `Nothing`,
    * failing a selection on it. [[pinTypeArgs]] declines here (no argument mentions it) — this
    * pins the DECLARATION's answer instead. Four conditions: no formal mentions the variable; no
    * TARGET TYPE; every variable has a REAL bound; the bound mentions no type variable of its own. */
  private[spoon] def pinUnconstrainedTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
    Option(inv.getExecutable.getExecutableDeclaration).collect { case m: CtMethod[?] => m } match
      case scala.None => fun
      case Some(m) =>
        val fs    = m.getFormalCtTypeParameters.asScala.toList
        val names = fs.map(_.getSimpleName).toSet
        val bounds = fs.map(f => Option(f.getSuperclass)
          .filter(_.getQualifiedName != "java.lang.Object").filterNot(mentionsNamedTypeVar))
        if fs.isEmpty || bounds.exists(_.isEmpty) then fun
        else if m.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, names)) then fun
        else if !isReceiverOfSelection(inv) then fun
        else Tree.TypeApply(fun, bounds.flatten.map(b => tt(tpe(b), inv)), NoType, o)

  /** G22's pin at the shape its FOURTH condition declines — an F-BOUND — by ascribing the
    * RESULT instead of instantiating the ARGUMENT (ENGINE-LIMITS G8.7): no denotable `X`
    * satisfies an F-bound as a type ARGUMENT, but the ascription supplies the TYPE THE
    * SELECTION READS while the argument still infers `Nothing`. Fires where G8's fill CANNOT be
    * written: G22's first three conditions plus the RESULT is the method's own variable. */
  private[spoon] def ascribeUnconstrainedResult(inv: CtInvocation[?], app: Term, o: Origin): Term =
    Option(inv.getExecutable.getExecutableDeclaration).collect { case m: CtMethod[?] => m } match
      case scala.None => app
      case Some(m) =>
        val fs    = m.getFormalCtTypeParameters.asScala.toList
        val names = fs.map(_.getSimpleName).toSet
        val resultVar = Option(m.getType).collect {
          case tp: CtTypeParameterReference if !tp.isInstanceOf[CtWildcardReference] && names(tp.getSimpleName) => tp
        }
        val bound = resultVar
          .flatMap(tp => fs.find(_.getSimpleName == tp.getSimpleName))
          .flatMap(f => Option(f.getSuperclass))
          .filter(_.getQualifiedName != "java.lang.Object")
        // ONLY where the type-argument pin declined, so one seam has one mechanism: a bound
        // with no named variable in it is G22's and is already answered there.
        if bound.isEmpty || !bound.exists(mentionsNamedTypeVar) then app
        else if m.getParameters.asScala.exists(p => mentionsTypeVar(p.getType, names)) then app
        else if !isReceiverOfSelection(inv) then app
        else
          // a variable the DECLARING TYPE owns is resolved through the RECEIVER's own
          // instantiation, exactly as G12 does at an argument: `IRichSequence<T>`'s `T`, read
          // from a `this` of type `IRichSequenceBase<T>`, IS this scope's `T` — a different
          // DECLARATION, so `sameVarInScope` alone answers no and would decline the whole
          // rule on the shape it exists for.
          val recvT  = Option(inv.getTarget).map(castType).orNull
          val ownerQ = Option(m.getDeclaringType).map(_.getQualifiedName)
          val viaRecv: String => Option[CtTypeReference[?]] = n =>
            ownerQ.flatMap(q => if recvT == null then scala.None else actualFor(recvT, q, n, 8))
          wildcardOwnVars(bound.get, names, viaRecv) match
            case Some(t)    => Tree.Typed(app, tt(t, inv), t, o)
            case scala.None => app

  /** the bound, with the METHOD's own variables rendered `?` and every other one required to be
    * writable here. `None` where one is not — see [[ascribeUnconstrainedResult]].
    *
    * The WILDCARD arm comes first for G22's own reason: Spoon's `CtWildcardReference` EXTENDS
    * `CtTypeParameterReference`, so a variable arm above it claims every `?`. */
  private[spoon] def wildcardOwnVars(r: CtTypeReference[?], own: Set[String],
                              viaRecv: String => Option[CtTypeReference[?]]): Option[TypeRepr] = r match
    case null                   => scala.None
    case w: CtWildcardReference => Some(tpe(w))
    case tp: CtTypeParameterReference =>
      if own(tp.getSimpleName) then Some(TypeBounds(NoType, NoType))
      else if tpNameableHere(tp) then Some(tpe(tp))
      else viaRecv(tp.getSimpleName).filter(tpNameableHere).map(tpe)
    case other =>
      val args = other.getActualTypeArguments.asScala.toList
      if args.isEmpty then Some(tpe(other))
      else
        val mapped = args.map(a => wildcardOwnVars(a, own, viaRecv))
        if mapped.exists(_.isEmpty) then scala.None
        else Some(AppliedType(TypeRef(NoPrefix, typeSym(other)), mapped.flatten))

  /** does this type mention a NAMED type variable — one an F-bound or an enclosing class
    * declares — as opposed to a WILDCARD, which is writable anywhere? `mentionsAnyTypeVar`
    * cannot answer it: Spoon's `CtWildcardReference` EXTENDS `CtTypeParameterReference`, so its
    * generic arm claims every `?` and the wildcard arm below it is dead. `Map<String, ?>` is the
    * bound this pin exists for. */
  private[spoon] def mentionsNamedTypeVar(tr: CtTypeReference[?]): Boolean = tr match
    case null                         => false
    case w: CtWildcardReference       => Option(w.getBoundingType).exists(mentionsNamedTypeVar)
    case _: CtTypeParameterReference  => true
    case arr: CtArrayTypeReference[?] => mentionsNamedTypeVar(arr.getComponentType)
    case r => r.getActualTypeArguments.asScala.exists(mentionsNamedTypeVar)

  /** does this invocation stand as the RECEIVER of another member access — the one position that
    * gives its result no expected type at all, and the one where scala's `Nothing` is then
    * selected from? See [[pinUnconstrainedTypeArgs]]. */
  private[spoon] def isReceiverOfSelection(inv: CtInvocation[?]): Boolean =
    inv.getParent match
      case p: CtInvocation[?]   => p.getTarget eq inv
      case p: CtFieldAccess[?]  => p.getTarget eq inv
      case _                    => false

  /** `int.class` etc. — Java types a primitive class literal as `Class<Integer>` (boxed), but we
    * emit it as `classOf[scala.Int]` (`Class[Int]`). Baseline inference binds a `Class<T>` param's
    * `T` to the primitive and matches; pinning `T` to the boxed wrapper would break that. So a
    * call carrying one of these must keep inference free — don't pin its type arguments. */
  private[spoon] def isPrimitiveClassLiteral(e: CtExpression[?]): Boolean = e match
    case fr: CtFieldRead[?] if fr.getVariable.getSimpleName == "class" =>
      fr.getTarget match
        case ta: CtTypeAccess[?] => ta.getAccessedType.isPrimitive
        case _                   => false
    case _ => false

  private[spoon] val boxedWrappers = Set(
    "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
    "java.lang.Character", "java.lang.Boolean", "java.lang.Float", "java.lang.Double")
  private[spoon] def isBoxedWrapper(t: CtTypeReference[?]): Boolean =
    boxedWrappers(t.getQualifiedName)

  private[spoon] def ctorCall(cc: CtConstructorCall[?])(using Obligations): Term =
    // A RAW `new` is the one place the inherited instantiation must NOT fill: the constructor
    // ARGUMENTS decide the parameter there. `new AssetDescriptor(name, TextureAtlas.class)`
    // inside `BitmapFontLoader extends …<BitmapFont, …>` is a `TextureAtlas` descriptor, not a
    // `BitmapFont` one. (Suppressing the fill for whole method BODIES instead measured 36 -> 59;
    // local declarations do need it to match the signatures they feed.)
    val savedNoInherit = noInheritFill
    noInheritFill = true
    val t    = tpe(cc.getType)
    noInheritFill = savedNoInherit
    val cid  = methodSym(cc.getExecutable)
    val argEs = cc.getArguments.asScala.toList
    // JS-G31, as at an invocation — the constructor's argument arms are three more of the same
    // family, and a `new` takes a lambda exactly as a call does. The row ATTACHES at the
    // invocation dispatch and this consult is recorded without being owed, which is the honest
    // shape: `Attaches` holds one surface, and a row nothing attaches here would still be a row
    // this arm had considered.
    val args = polyArgsAscribed(cc.getExecutable, argEs, polyArgsUncast(
      argEs, appliedCtorArgs(cc, argEs, rawCtorArgs(cc, argEs, coerceArgs(cc.getExecutable, argEs, originOf(cc)))), originOf(cc)))
    // `CtNewClass` IS a `CtConstructorCall` — the anonymous body hangs off the subtype, and
    // reading only the supertype is what silently dropped every one of them.
    val anon = cc match
      case nc: CtNewClass[?] => anonClass(nc, classId, varScope)
      case _                 => None
    // JS-C31 — anonymous class construction and capture; JS-C17 — DOUBLE-BRACE INITIALISATION,
    // that construct plus an instance initialiser and nothing else. Consulted here rather than
    // inside `anonClass`, because a row consulted only where it fires says nothing. A plain
    // `CtConstructorCall` records both without being owed them — the attachment is at
    // `CtNewClass`, which is the kind that carries the body.
    Obligations.consult(JS.C(31), originOf(cc))(Option.when(anon.isDefined)(()))
    Obligations.consult(JS.C(17), originOf(cc))(Option.when(cc match
      case nc: CtNewClass[?] =>
        Option(nc.getAnonymousClass).exists(!_.getAnonymousExecutables.isEmpty)
      case _ => false)(()))
    // JS-G10 — a RAW anonymous class WITH a body, which is REFUSED rather than approximated
    // (`ENGINE-LIMITS.md` G10): without a body scala infers the argument from the expected type,
    // and with one the anonymous class's type is fixed, so a raw use gives `Parent[Nothing]` and
    // naming the argument does not help either. Consulted at the kind that carries the body, so
    // the refusal is a decision the coverage lane can count rather than a silence.
    Obligations.consult(JS.G(10), originOf(cc))(Option.when(cc match
      case nc: CtNewClass[?] => anon.isDefined && isRawGenericUse(nc.getType)
      case _                 => false)(()))
    Tree.Apply(Tree.New(tt(t, cc), t, originOf(cc), anon), args, cid, t, originOf(cc))

  /** A RAW constructor call — `return new Values(this)` inside `ArrayMap<K,V>`. Java checks a
    * raw `new`'s arguments against the ERASED constructor, so `this: ArrayMap<K,V>` passes
    * unchecked. We render the raw type name-FILLED (`new Values[V](…)`), re-imposing the
    * un-erased formal — so arguments must be filled by the SAME name-directed rule, or the two
    * halves of one raw use disagree. Only for formals that mention a type variable resolving here. */
  private[spoon] def rawCtorArgs(cc: CtConstructorCall[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
    if !isRawGenericUse(cc.getType) then args
    else
      val ps = execDeclOf(cc.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
      ps match
        case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
          val specialised = rawCtorSpecialisation(cc, l, argEs, args)
          args.zipWithIndex.map { (t, i) =>
            val f = l(i)
            if f == null || !isGenericUse(f) || !mentionsAnyTypeVar(f) || !tpAccessibleHere(f) then
              specialised.get(i).fold(t)(ct => Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin))
            else
              val ct = tpe(f)
              if ct == t.tpe || hasWildcard(ct) then t else Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
          }
        case _ => args

  /** SPECIALISE the erased arguments of a raw constructor call, rather than erasing the precise
    * ones — java checked none of it (constructor is raw), but SOME instantiation must be
    * chosen, recovered from a precise argument's own supertype chain. Casting the OTHER way
    * (precise argument DOWN to erased) measured worse (23/5/43 errors, ENGINE-LIMITS G13).
    * Narrow: one class type parameter, one binding found, only arguments AT the erasure touched. */
  private[spoon] def rawCtorSpecialisation(
      cc: CtConstructorCall[?], l: List[CtTypeReference[?]], argEs: List[CtExpression[?]], args: List[Term],
  ): Map[Int, TypeRepr] =
    val clsFormals = Option(cc.getType).flatMap(typeDeclarationOf)
                           .map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
    if clsFormals.sizeIs != 1 then Map.empty
    else
      val v = clsFormals.head.getSimpleName
      val erased = erasureOfFormal(clsFormals.head, Set.empty, 2)
      // where does the class variable sit inside a formal, and what does the ACTUAL argument
      // bind it to? Only `G<...V...>` shapes; the argument's own supertype chain supplies it.
      def bindingFrom(f: CtTypeReference[?], e: CtExpression[?]): Option[CtTypeReference[?]] =
        val idx = f.getActualTypeArguments.asScala.toList.indexWhere {
          case tv: CtTypeParameterReference => tv.getSimpleName == v
          case _                            => false
        }
        if idx < 0 then None
        else
          val head = f.getQualifiedName
          def walk(t: CtTypeReference[?], depth: Int): Option[CtTypeReference[?]] =
            if t == null || depth <= 0 then None
            else if t.getQualifiedName == head then
              t.getActualTypeArguments.asScala.toList.lift(idx)
                .filterNot(a => a == null || a.isInstanceOf[CtWildcardReference] ||
                                a.isInstanceOf[CtTypeParameterReference])
            else
              val ups = Option(t.getSuperclass).toList ++ t.getSuperInterfaces.asScala.toList
              ups.iterator.flatMap(u => walk(u, depth - 1)).nextOption()
          try walk(e.getType, 6) catch { case _: Throwable => None }

      val bindings = l.zipWithIndex.flatMap { (f, i) =>
        if f == null then Nil else bindingFrom(f, argEs(i)).map(b => tpe(b)).toList
      }.distinct
      bindings match
        case List(b) if b != erased =>
          // only rewrite the arguments that are AT the erasure — those are the ones whose own
          // rendering lost the instantiation and would otherwise pin `T` to `Object`.
          l.zipWithIndex.flatMap { (f, i) =>
            if f == null || !mentionsTypeVarFilled(f, Set(v)) then Nil
            else
              val at = erasedFormal(f, Map(v -> erased))
              if at != args(i).tpe then Nil
              // `erasedFormal` resolves `subst` only for a BARE type variable; inside
              // `Class<T>` it erases the nested `T` regardless, so it hands back the same
              // `Class[Object]` we are trying to move away from. Substitute on the RENDERED
              // type instead — sound precisely because this arm already established that the
              // argument sits AT the erasure, so every `Object` in it stands for `v`.
              else List(i -> substRepr(at, erased, b))
          }.toMap
        case _ => Map.empty

  /** An APPLIED generic constructor call whose argument java unchecked-converted — read through
    * the erased receiver view, scala needs the conversion java made implicitly written out.
    * Target is the declared formal with the class's own parameters substituted by the call's
    * EXPLICIT type arguments (else `uncheckedGeneric` would render `?T`). Raw counterpart is
    * [[rawCtorArgs]]. Gated on the ARGUMENT mentioning a raw generic. */
  private[spoon] def appliedCtorArgs(cc: CtConstructorCall[?], argEs: List[CtExpression[?]], args: List[Term]): List[Term] =
    val actuals = cc.getType.getActualTypeArguments.asScala.toList
    val formals = Option(cc.getType).flatMap(typeDeclarationOf)
                        .map(_.getFormalCtTypeParameters.asScala.toList.map(_.getSimpleName)).getOrElse(Nil)
    val ps = execDeclOf(cc.getExecutable).map(_.getParameters.asScala.toList.map(_.getType))
    if actuals.isEmpty || actuals.sizeIs != formals.size || !tpAccessibleHere(cc.getType) then args
    else ps match
      case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
        val subst = formals.zip(actuals.map(tpe)).toMap
        args.zipWithIndex.map { (t, i) =>
          val f  = l(i)
          // the same expression kinds `uncheckedGeneric` refuses: a class literal types as raw
          // `Class` yet emits as `classOf[X]`, and casting a literal/lambda/array-initialiser
          // only destroys the inference it feeds.
          val bad = argEs(i) match
            case fr: CtFieldRead[?] => fr.getVariable.getSimpleName == "class"
            case e                  => polyExpression(e) || e.isInstanceOf[CtLiteral[?]] ||
                                       e.isInstanceOf[CtNewArray[?]] || e.isInstanceOf[CtConditional[?]]
          if f == null || bad || !mentionsAnyTypeVar(f) then t
          else substFormal(f, subst) match
            case Some(ct) if ct != t.tpe && !hasWildcard(ct) => Tree.Typed(t, tt(ct, argEs(i)), ct, t.origin)
            case _                                           => t
        }
      case _ => args

  /** SymId of a called executable — via its declaration (keyed identically to how we define our
    * own methods) or, for unresolved externals, by its reference. Under `noClasspath`,
    * `getExecutableDeclaration` can resolve to an UNRELATED same-named method — guarded
    * STRUCTURALLY (declaration's owner must be the receiver's type or a SUPERTYPE); disagreement
    * falls through to interning by the RECEIVER's declaring type (CLAUDE.md §4.56, ENGINE-LIMITS G34). */
  private[spoon] def methodSym(ex: CtExecutableReference[?]): SymId =
    Option(ex.getExecutableDeclaration).filter(decl => declAgrees(decl, ex)) match
      case Some(decl) =>
        val (q, s) = declType(decl)
        val ownerId = minter.external(q, s)
        val nm      = if decl.isInstanceOf[CtConstructor[?]] then "<init>" else decl.getSimpleName
        externalMember(ownerId, nm + erasedSig(decl), nm, descriptorOf(decl), externalSignature(decl))
      case None =>
        val ownerQ  = Option(ex.getDeclaringType).map(_.getQualifiedName).getOrElse("java.lang.Object")
        val ownerId = minter.external(ownerQ, simpleName(ownerQ))
        val nm      = if ex.isConstructor then "<init>" else ex.getSimpleName
        val sig     = ex.getParameters.asScala.toList.map(p => scala.util.Try(p.getQualifiedName).getOrElse("?")).mkString(",")
        // NO DESCRIPTOR, deliberately. With no declaration this is the REFERENCE's formals,
        // which a lenient parse erases systematically (`<T> void m(T)` reads `m(Object)`), so
        // recording them would manufacture a precise-looking key that names the wrong overload.
        // This is the design's admitted residue: the failure is LOUD at bind time — an unbound
        // key naming a real member — instead of a silent degrade to arity at match time.
        externalMember(ownerId, s"$nm($sig)", nm)

  /** Does the resolved declaration's declaring type agree with the reference's declaring type?
    * The owner must be the SAME type or a SUPERTYPE of the reference's declaring type; an
    * unrelated type sharing a name does not agree. Where no declaring type is available on the
    * REFERENCE, the declaration is ACCEPTED (§4.56: state the refutation, not reject every
    * untyped reference). Uses [[typeDeclarationOf]] as [[declaringStaticType]] does. */
  private[spoon] def declAgrees(decl: CtExecutable[?], ref: CtExecutableReference[?]): Boolean =
    val refDeclType = Option(ref.getDeclaringType)
    refDeclType match
      case scala.None => true // no reference type to compare — accept the declaration
      case Some(refType) =>
        val (declQ, _) = declType(decl)
        val refQ = refType.getQualifiedName
        // same type — the common case
        if declQ == refQ then true
        // the declaration's type is a supertype of the reference's type — inherited method
        else isSupertypeOf(refType, declQ)

  /** Is `targetQ` a supertype of `start`? BFS over the hierarchy — superclass then
    * superinterfaces — same shape as [[selfAndAncestors]]. Where the hierarchy is unreadable the
    * walk stops at that node and answers `false` (the safe direction: a mis-resolution falls
    * through to the reference branch). No bare `catch` — parents read through
    * [[typeDeclarationOf]] (the one lookup where absence is normal, §4.6). */
  private[spoon] def isSupertypeOf(start: CtTypeReference[?], targetQ: String): Boolean =
    val seen  = collection.mutable.Set[String]()
    val queue = collection.mutable.Queue[CtTypeReference[?]]()
    queue.enqueue(start)
    var found = false
    while queue.nonEmpty && !found do
      val r = queue.dequeue()
      if r != null then
        val q = r.getQualifiedName
        if q != null && seen.add(q) then
          typeDeclarationOf(r) match
            case Some(t) =>
              val parents: List[CtTypeReference[?]] =
                (t match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                  t.getSuperInterfaces.asScala.toList
              parents.foreach { p =>
                if p != null then
                  if p.getQualifiedName == targetQ then found = true
                  else queue.enqueue(p)
              }
            case scala.None =>
              // no declaration available — stop the walk at this node.
              // The safe direction (§4.56): an unresolvable hierarchy cannot confirm the
              // declaration agrees, so we decline and fall through to the reference branch.
              ()
    found

  private[spoon] def declType(decl: CtExecutable[?]): (String, String) = decl match
    case tm: CtTypeMember if tm.getDeclaringType != null => (tm.getDeclaringType.getQualifiedName, tm.getDeclaringType.getSimpleName)
    case _ =>
      val t = decl.getParent(classOf[CtType[?]])
      if t != null then (t.getQualifiedName, t.getSimpleName) else ("java.lang.Object", "Object")

  private[spoon] def typeTerm(ta: CtTypeAccess[?], at: CtElement): Term =
    val q  = ta.getAccessedType.getQualifiedName
    val id = minter.external(q, simpleName(q))
    Tree.Ident(id, TypeRef(NoPrefix, id), originOf(at))

  /** T14 — the receiver of a STATIC CALL is the member's DECLARING type, not the type the
    * source wrote (java lets a static be named through ANY subclass; scala companions inherit
    * nothing — 20 errors on one library from a single upstream idiom). Read off the SYMBOL'S
    * OWNER, never the written name (§4.56); re-qualifies for an IN-PROGRAM parent too. */
  private[spoon] def staticCallQualifier(ta: CtTypeAccess[?], mid: SymId, at: CtElement): Term =
    val written  = ta.getAccessedType.getQualifiedName
    val declaring = minter.ownerOf(mid)
    if declaring != SymId.None && minter.fullNameOf(declaring) != written then
      Tree.Ident(declaring, TypeRef(NoPrefix, declaring), originOf(at))
    else typeTerm(ta, at)

  // operators as `recv.op(args)` — the quotes.reflect shape (no dedicated node).
  private[spoon] def opId(op: String): SymId = minter.external("scala.<op>#" + op, op)
  /** `i++`/`i--` on a byte/short/char narrows (`i = (short)(i + 1)`) — cast the result back. */
  private[spoon] def incNarrow(opnd: CtExpression[?], res: Term): Term =
    val ot = opnd.getType
    if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
    then Tree.Typed(res, tt(tpe(ot), opnd), tpe(ot), originOf(opnd)) else res

  /** The narrowing TYPE for an increment, or `None` — the same predicate as `incNarrow`, returning
    * the type rather than wrapping. Used by the statement arm where the narrowing is carried on
    * `Tree.Assign.compound` rather than wrapped in `Tree.Typed`. */
  private[spoon] def incNarrowType(opnd: CtExpression[?]): Option[TypeRepr] =
    val ot = opnd.getType
    if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
    then Some(tpe(ot)) else scala.None

  private[spoon] def isStringConcat(b: CtBinaryOperator[?]): Boolean =
    Option(b.getType).exists(_.getQualifiedName == "java.lang.String")
  private[spoon] def isStringTyped(e: CtExpression[?]): Boolean =
    Option(e.getType).exists(_.getQualifiedName == "java.lang.String")
  /** `java.lang.String.valueOf(t)` — make a non-String operand a String for concatenation. */
  private[spoon] def stringify(t: Term, el: CtElement): Term =
    val strSym = minter.external("java.lang.String", "String")
    val vSym   = minter.external("java.lang.String#valueOf", "valueOf", strSym)
    Tree.Apply(Tree.Select(Tree.Ident(strSym, TypeRef(NoPrefix, strSym), originOf(el)), vSym, NoType, originOf(el)),
      List(t), vSym, TypeRef(NoPrefix, strSym), originOf(el))

  /** Java's `==` between REFERENCE types is identity; scala's `==` is `equals` — inside an
    * `equals` implementation that is infinite recursion (151 sites). `eq` is faithful for boxed
    * wrappers/enums/interned Strings too; skipped for `null` or PRIMITIVE operands. */
  /** JS-E14's PREDICATE: a NON-`String` left operand is stringified (`String.valueOf(obj)+"s"`);
    * `scala.None` for every other operator. */
  private[spoon] def stringConcatLeft(b: CtBinaryOperator[?]): Option[Term] =
    if b.getKind == BinaryOperatorKind.PLUS && isStringConcat(b) && !isStringTyped(b.getLeftHandOperand)
    then Some(binApply("+", stringify(expr(b.getLeftHandOperand), b), expr(b.getRightHandOperand), ty(b)))
    else scala.None

  /** JS-E02's PREDICATE: `++`/`--` in either position, which java evaluates to the value BEFORE
    * the update for the postfix forms. `Tree.IncDec` carries the distinction; the emitter is
    * what renders `{ val p = x; x += 1; p }` rather than `{ x += 1; x }`. */
  private[spoon] def incDecOf(u: CtUnaryOperator[?]): Option[Term] =
    import UnaryOperatorKind.*
    u.getKind match
      case POSTINC => Some(Tree.IncDec(expr(u.getOperand), "+", post = true, ty(u), originOf(u)))
      case POSTDEC => Some(Tree.IncDec(expr(u.getOperand), "-", post = true, ty(u), originOf(u)))
      case PREINC  => Some(Tree.IncDec(expr(u.getOperand), "+", post = false, ty(u), originOf(u)))
      case PREDEC  => Some(Tree.IncDec(expr(u.getOperand), "-", post = false, ty(u), originOf(u)))
      case _       => scala.None

  private[spoon] def referenceIdentity(b: CtBinaryOperator[?]): Option[Term] =
    import BinaryOperatorKind.*
    val (l, r) = (b.getLeftHandOperand, b.getRightHandOperand)
    def isNull(e: CtExpression[?]) = e match { case lit: CtLiteral[?] => lit.getValue == null; case _ => false }
    def refTyped(e: CtExpression[?]) =
      val t = e.getType
      t != null && !t.isPrimitive
    if (b.getKind != EQ && b.getKind != NE) || isNull(l) || isNull(r) then scala.None
    else if !refTyped(l) || !refTyped(r) then scala.None
    else
      val anyRef = TypeRef(NoPrefix, minter.external("scala.AnyRef", "AnyRef"))
      val anyT = TypeRef(NoPrefix, minter.external("scala.Any", "Any"))
      // `eq` lives on `AnyRef`. A `java.lang.Object` operand may have been rendered `Any` —
      // java's `equals(Object)` parameter must be, since scala's `Object.equals` takes `Any` —
      // and the emitted term still carries spoon's type, so the rendering cannot be read off
      // it. Ascribe on the java type instead: every `Object` value IS an `AnyRef`, so this is
      // a no-op wherever the widening was not needed.
      def asRef(e: CtExpression[?]): Term =
        val t = expr(e)
        val objTyped = Option(e.getType).exists(_.getQualifiedName == "java.lang.Object")
        if objTyped || t.tpe == anyT then Tree.Typed(t, tt(anyRef, e), anyRef, originOf(e)) else t
      val op = if b.getKind == EQ then "eq" else "ne"
      Some(binApply(op, asRef(l), asRef(r), ty(b)))

  private[spoon] def binApply(op: String, l: Term, r: Term, resT: TypeRepr): Term =
    Tree.Apply(Tree.Select(l, opId(op), NoType, l.origin), List(r), opId(op), resT, l.origin)
  private[spoon] def unApply(op: String, o: Term, resT: TypeRepr): Term =
    Tree.Apply(Tree.Select(o, opId(op), NoType, o.origin), Nil, opId(op), resT, o.origin)

  /** the Scala spelling of a java binary operator, or `scala.None` for a kind this arm does not
    * enumerate. An `Option`, not a defaulted string: `BinaryOperatorKind` is a java enum scalac
    * cannot check, and a fabricated name (`"?" + other`) would silently become a real method
    * call in `binApply`. `INSTANCEOF` (java's twentieth kind) never reaches here; enumerates
    * the other nineteen. */
  private[spoon] def opText(k: BinaryOperatorKind): Option[String] =
    import BinaryOperatorKind.*
    k match
      case PLUS => Some("+"); case MINUS => Some("-"); case MUL => Some("*")
      case DIV => Some("/"); case MOD => Some("%")
      case AND => Some("&&"); case OR => Some("||"); case BITAND => Some("&")
      case BITOR => Some("|"); case BITXOR => Some("^")
      case EQ => Some("=="); case NE => Some("!="); case LT => Some("<")
      case LE => Some("<="); case GT => Some(">"); case GE => Some(">=")
      case SL => Some("<<"); case SR => Some(">>"); case USR => Some(">>>")
      case _ => scala.None

  /** the MARKER for an operator kind [[opText]] does not enumerate — located, named, and
    * `FrontendBlindSpot` rather than `UnmodelledNodeKind` because the node KIND is dispatched
    * on here and what is missing is one shape of it. The emission gate then refuses to ship the
    * port until it is closed (`DESIGN.md` §6.4), which is the whole difference between this and
    * a method name nobody can resolve. */
  private[spoon] def unknownOp(k: BinaryOperatorKind, at: CtElement, tpe: TypeRepr): Term =
    unlowered(at, s"binary operator kind '$k' — this arm enumerates java's nineteen and the " +
      "parser produced a twentieth", tpe, Some(UnportableKind.FrontendBlindSpot))
