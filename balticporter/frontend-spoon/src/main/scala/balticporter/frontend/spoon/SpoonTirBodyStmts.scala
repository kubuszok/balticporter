package balticporter.frontend.spoon

// Split out of SpoonTir.scala for file size (context diet S2): BodyTranslator's statement-side lowering.

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

private[spoon] trait SpoonTirBodyStmts:
  this: SpoonTirBodyCore & SpoonTirBodyExprs =>

  private[spoon] def methodBody(b: CtBlock[?]): Term = blockOf(b.getStatements.asScala.toList, b)

  /** a statement list and the element it came from → a TIR `Block`, with whatever comments
    * were written after the last statement kept in the block's `trailing` slot.
    *
    * The ONE place a `Tree.Block` is built out of `stmts`: the leftover was previously dropped
    * at each of the three call sites independently, which is exactly the shape that makes a
    * fix land in two of them. */
  private[spoon] def blockOf(ss: List[CtStatement], el: CtElement): Tree.Block =
    val (sts, trail) = stmts(ss)
    Tree.Block(sts, unit(el), unitT, originOf(el), trail)

  // ---- statement trivia ---------------------------------------------------
  // ORDER matters: (1) leadingOf(s) claims Spoon's own attachment, (2) stmt(s) translates
  // (nested statements claim their own), (3) deepComments(s) scoops what is left. A trailing
  // comment with nothing after it goes to `Tree.Block.trailing`, never discarded.

  /** Translate a statement list, folding comment-statements into the statement that follows.
    * Second half of the pair is what is LEFT (a block's `trailing`) — returned, not attached,
    * since not every statement list is a block. */
  private[spoon] def stmts(ss: List[CtStatement]): (List[Statement], List[Trivia]) =
    val out     = List.newBuilder[Statement]
    var pending = List.empty[Trivia]
    ss.foreach {
      case c: CtComment => claimed.add(c); pending = pending :+ triviaOf(c)
      case s =>
        out += withTrivia(pending, s)
        pending = Nil
    }
    (out.result(), pending)

  /** one statement, with `pending` plus its own plus its subtree's leftovers attached. */
  private[spoon] def withTrivia(pending: List[Trivia], s: CtStatement): Statement =
    val own = leadingOf(s)
    val k   = stmt(s)
    val all = pending ++ own ++ deepComments(s)
    if all.isEmpty then k
    else
      k match
        // declarations (ValDef/ClassDef/DefDef) carry their own `leading` field — no `Tree.Commented` wrapper (not a Term)
        case v: Tree.ValDef   => v.copy(leading = all ++ v.leading)
        case c: Tree.ClassDef => c.copy(leading = all ++ c.leading)
        case d: Tree.DefDef   => d.copy(leading = all ++ d.leading)
        case t: Term          => TirTrace.mint(Tree.Commented(all, t))
        case other            => other

  private[spoon] def exprOf(e: CtExpression[?]): Term = expr(e)
  /** translate an initializer, coercing it to `target` (null → type param, narrowing, etc.). */
  private[spoon] def coercedExprOf(target: CtTypeReference[?], e: CtExpression[?]): Term = coerce(target, e, expr(e))

  private[spoon] def unit(el: CtElement): Term = Tree.Literal(Constant.UnitC, unitT, originOf(el))

  private[spoon] def blockTerm(s: CtStatement): Term = s match
    case null          => Tree.Block(Nil, Tree.Literal(Constant.UnitC, unitT, Origin.synthetic), unitT, Origin.synthetic)
    case b: CtBlock[?] => blockOf(b.getStatements.asScala.toList, b)
    case single        => Tree.Block(List(withTrivia(Nil, single)), unit(single), unitT, originOf(single))

  // ---- statements ----

  /** One statement, with a java LABEL on a non-loop statement turned into [[Tree.Labeled]] —
    * `break L` leaves THAT statement, so a labelled `if`/block/`switch` needs a node. A LOOP's
    * label is read into its own node field instead (also `continue L`'s target). */
  private[spoon] def stmt(s: CtStatement): Statement =
    val k = stmtKind(s)
    labelOf(s) match
      // a labelled loop already carries its label; a `ValDef` cannot be labelled (JLS 14.7)
      case Some(l) if !carriesOwnLabel(k) =>
        k match
          case t: Term => TirTrace.mint(Tree.Labeled(l, t, unitT, originOf(s)))
          case other   => other
      case _ => k

  /** does this translated statement already hold its java label in a field of its own? */
  private[spoon] def carriesOwnLabel(k: Statement): Boolean = k match
    case _: Tree.While | _: Tree.For | _: Tree.ForEach | _: Tree.DoWhile => true
    case _                                                               => false

  /** JS-E03/E04's PREDICATE, as ONE function (not copied at its two consult sites): the target
    * type when java's implicit narrowing applies to a compound assignment, else `None`. Narrows
    * whenever `max(rhsRank, intRank) > targetRank` (java's binary numeric promotion). */
  private[spoon] def compoundNarrow(a: CtOperatorAssignment[?, ?]): Option[CtTypeReference[?]] =
    val lt = a.getAssigned.getType
    val rt = a.getAssignment.getType
    val narrow = lt != null && lt.isPrimitive && rt != null && rt.isPrimitive &&
      primRank.get(lt.getSimpleName).exists(l =>
        primRank.get(rt.getSimpleName).exists(r => math.max(r, primRank("int")) > l))
    if narrow then Some(lt) else scala.None

  /** THE STATEMENT DISPATCH — obligation wrapper sits HERE, not per-arm, so no arm can opt out
    * of it (DESIGN.md §2.8). `Lowering.of` maps the runtime class to its registry name once. */
  private[spoon] def stmtKind(s: CtStatement): Statement =
    // `s` is the SUBJECT — the node itself, so a delegation into the expression dispatch
    // (`case inv: CtInvocation => expr(inv)`) can be joined to this scope by identity.
    Lowering.of(SpoonKinds.nameOf(s.getClass), Dispatch.Statement, originOf(s), s)(stmtArm(s))

  private[spoon] def stmtArm(s: CtStatement)(using Obligations): Statement = s match
    case v: CtLocalVariable[?] =>
      val vt = tpe(v.getType)
      val id = defineLocal(v, vt) // sets isMutable when the local is reassigned
      // JS-G09/G13/G14 slot rows — no initialiser means empty list, honest "does not apply"
      slotConsults(Option(v.getDefaultExpression).map(v.getType -> _).toList, originOf(v))
      val rhs = Option(v.getDefaultExpression).map(e => coerce(v.getType, e, expr(e)))
      Tree.ValDef(id, tt(vt, v), rhs, originOf(v))
    case a: CtOperatorAssignment[?, ?] =>
      // Java compound assignment narrows implicitly: `int += float` means `= (int)(i + f)`.
      val lhs = expr(a.getAssigned)
      val rhs = expr(a.getAssignment)
      val op  = opText(a.getKind).getOrElse { unknownOp(a.getKind, a, ty(a)); "?" }
      // JS-E03 CONSULTED, not just done, so the coverage lane can count the decision
      val narrow = Obligations.consult(JS.E(3), originOf(a))(compoundNarrow(a))
        .map(t => tpe(t))
      // JS-E17: lvalue single evaluation (F7) — emitter binds non-trivial subexpressions once
      Obligations.consult(JS.E(17), originOf(a))(Some(()))
      Tree.Assign(lhs, rhs, unitT, originOf(a), compound = Some((op, narrow)))
    case a: CtAssignment[?, ?] =>
      val tgt = Option(a.getAssigned.getType)
      val rhs = a.getAssignment
      val lhs = expr(a.getAssigned)
      slotConsults(tgt.map(_ -> rhs).toList, originOf(a))
      val v   = tgt.map(coerce(_, rhs, expr(rhs))).getOrElse(expr(rhs))
      Tree.Assign(lhs, toDeclaredTypeParam(a.getAssigned, rhs, v), unitT, originOf(a))
    case i: CtIf =>
      val elze = Option(i.getElseStatement).map(blockTerm).getOrElse(unit(i))
      Tree.If(expr(i.getCondition), blockTerm(i.getThenStatement), elze, unitT, originOf(i))
    case r: CtReturn[?] =>
      // coerce the returned value to the method's declared return type (null → type param, etc.).
      val target = Option(r.getParent(classOf[CtMethod[?]])).flatMap(m => Option(m.getType))
      slotConsults(target.zip(Option(r.getReturnedExpression)).toList, originOf(r))
      val ret = Option(r.getReturnedExpression).map(e => target.map(tp => coerce(tp, e, expr(e))).getOrElse(expr(e)))
      Tree.Return(ret, nothingT, originOf(r))
    case w: CtWhile =>
      Tree.While(expr(w.getLoopingExpression), blockTerm(w.getBody), unitT, originOf(w), labelOf(w))
    case t: CtThrow =>
      Tree.Throw(expr(t.getThrownExpression), nothingT, originOf(t))
    case b: CtBlock[?]      => blockTerm(b)
    case inv: CtInvocation[?] => expr(inv)
    case cc: CtConstructorCall[?] => ctorCall(cc)
    case f: CtForEach =>
      val v  = f.getVariable
      val vt = tpe(v.getType)
      val id = defineLocal(v, vt)
      Tree.ForEach(Tree.ValDef(id, tt(vt, v), None, originOf(v)), iterableOperand(f.getExpression), blockTerm(f.getBody), unitT, originOf(f), labelOf(f))
    case f: CtFor =>
      val init = f.getForInit.asScala.toList.map(stmt)
      val cond = Option(f.getExpression).map(expr)
      val upd  = f.getForUpdate.asScala.toList.map(stmt)
      Tree.For(init, cond, upd, blockTerm(f.getBody), unitT, originOf(f), labelOf(f))
    case t: CtTryWithResource =>
      // SE9 form (`try (existingLocal)`, JLS 14.20.3) is a variable REFERENCE, not a
      // `CtLocalVariable` — refused LOUDLY (M6) rather than silently closing one resource fewer
      val res = t.getResources.asScala.toList.map {
        case lv: CtLocalVariable[?] =>
          val rt = tpe(lv.getType)
          Tree.ValDef(defineLocal(lv, rt), tt(rt, lv), Option(lv.getDefaultExpression).map(expr), originOf(lv))
        case other =>
          unsupported(other, "a try-with-resources resource that is not a local DECLARATION " +
            "(JLS 14.20.3's SE9 form, an existing effectively-final variable): it needs a fresh " +
            "alias binding to close, and dropping it closes one resource fewer than java does")
      }
      tryStmt(t, res)
    case t: CtTry             => tryStmt(t, Nil)
    case s: CtSwitch[?]       => switchStmt(s)
    case b: CtBreak           => Tree.Break(Option(b.getTargetLabel), nothingT, originOf(b))
    // `yield v` — JLS 14.21, and only ever a NON-TAIL one by the time it is reached from here:
    // a switch-expression arm's LAST statement is peeled into the arm's value by `armValue`,
    // and an arrow-form STATEMENT arm's Spoon-synthesised wrapper is undone by `caseBody`. What
    // is left is a `yield` that leaves the arm from inside an `if` or a nested block, which
    // scala can only express as a value-carrying `boundary` the emitter puts around the ARM.
    case y: CtYieldStatement  => Tree.Yield(expr(y.getExpression), nothingT, originOf(y))
    case c: CtContinue        => Tree.Continue(Option(c.getTargetLabel), nothingT, originOf(c))
    case a: CtAssert[?]       => Tree.Assert(expr(a.getAssertExpression), Option(a.getExpression).map(expr), unitT, originOf(a))
    case d: CtDo              =>
      // JS-S18, the FRONTEND half — Scala 3 removed `do`-`while`, so there is no keyword to map
      // to and the loop needs a node of its own for the emitter to give it a shape. Always
      // fires: every java `do` needs the image. The row attaches at BOTH surfaces, and this
      // consult is why — the emitter's alone would claim coverage for a decision taken here.
      Obligations.consult(JS.S(18), originOf(d))(Some(()))
      Tree.DoWhile(blockTerm(d.getBody), expr(d.getLoopingExpression), unitT, originOf(d), labelOf(d))
    case y: CtSynchronized    =>
      // JS-S22 — java's `synchronized` STATEMENT has a scala image with the same monitor
      // bytecode (`.synchronized`), and choosing it is the whole content of this row. Always
      // fires: every `synchronized` block needs the mapping.
      Obligations.consult(JS.S(22), originOf(y))(Some(()))
      Tree.Synchronized(expr(y.getExpression), blockTerm(y.getBlock), unitT, originOf(y))
    case u: CtUnaryOperator[?] =>
      import UnaryOperatorKind.*
      val one = Tree.Literal(Constant.IntC(1), ty(u), originOf(u))
      u.getKind match
        case POSTINC | PREINC =>
          val t = expr(u.getOperand)
          val narrow = incNarrowType(u.getOperand)
          Tree.Assign(t, one, unitT, originOf(u), compound = Some(("+", narrow)))
        case POSTDEC | PREDEC =>
          val t = expr(u.getOperand)
          val narrow = incNarrowType(u.getOperand)
          Tree.Assign(t, one, unitT, originOf(u), compound = Some(("-", narrow)))
        case _                => expr(u)
    // A free-floating comment arriving as a STATEMENT. `stmts` folds these into the statement
    // that follows, so one reaching here is a body that is ONLY a comment (`if (x) /* no-op */;`)
    // — Java's empty statement. NOT claimed: leaving it unclaimed lets the enclosing
    // statement's `deepComments` pick the text up, which is the only place left to put it.
    case c: CtComment => Tree.Literal(Constant.UnitC, unitT, originOf(c))
    // A METHOD-LOCAL NAMED CLASS — JLS 14.3, catalog JS-C30. `Tree.ClassDef` is a `Statement`,
    // so the node the TIR needs already existed; what was missing was the arm. Two things this
    // arm decides that the DECLARATION path does not:
    //
    //   - the OWNER is the enclosing EXECUTABLE, not the enclosing type. Spoon reports a
    //     declaring TYPE for a local class (it is nested in the binary name), and taking that
    //     would make every "is this a member of `Outer`?" question answer yes: the emitter
    //     would render `Outer#Local`, a type projection naming a member that does not exist.
    //     Owning it by the method is also the structurally true statement — §4.56's ownership
    //     chain still reaches the unit through the method, so the symbol stays OWNED;
    //   - the NAME is java's SOURCE name. Spoon's qualified name carries the binary
    //     disambiguator (`p.Outer$1Local`), which is the right INTERNING key — the `new Local()`
    //     reference resolves through it — and is not a legal Scala identifier.
    //
    // Captures need no lowering, exactly as for an anonymous class: javac synthesises
    // constructor parameters for them and Scala closes over them directly.
    case c: CtClass[?] =>
      // JS-C30, consulted rather than merely done: the catalog attaches the row to THIS
      // dispatch, so the wrapper reports an arm that returns without asking. It fires at every
      // local class, which is the whole population the row is about — a `CtClass` reaching the
      // STATEMENT dispatch is a local class by construction, since every other one is walked
      // from its declaring type.
      Obligations.consult(JS.C(30), originOf(c))(Some(()))
      classDef(c, owner = Some(methodId), sourceName = Some(localName(c)),
               selfClass = classId, outerVars = varScope)
    // NO ARM EXISTS for this Java statement kind. A MARKER, not a throw: the failure is the
    // size of the construct rather than the size of the file, and the gate still refuses to
    // ship the port (§6.4). `unitT` because a statement produces no value.
    case other => unlowered(other, s"statement ${SpoonKinds.nameOf(other.getClass)}", unitT)

  /** THE ENHANCED-FOR'S ITERABLE, at the type JAVA READ IT AT (ENGINE-LIMITS G31). JLS 14.14.2
    * iterates at `Iterable<T>` found among the supertypes, not the expression's own type — an
    * F-BOUNDED wildcard capture fails at an INFERRED type in scala (`E057`) unless ascribed here.
    * Ordinary bounded wildcards are left alone (capture-convert unaided, §5's widening rule).
    * Declines where the found `Iterable` argument mentions a type VARIABLE (§4.6). */
  private[spoon] def iterableOperand(e: CtExpression[?]): Term =
    val t  = expr(e)
    val et = try Option(e.getType) catch { case _: Throwable => scala.None }
    et.filter(fboundWildcardUse).flatMap(javaIterableSuper) match
      case Some(iter) => val ty = tpe(iter); Tree.Typed(t, tt(ty, e), ty, originOf(e))
      case scala.None => t

  /** is this an application with a WILDCARD at a SELF-REFERENTIALLY bounded slot — the one shape
    * scala's capture conversion cannot answer? Read off the DECLARATION's own bounds, and the
    * unreadable answer is `false`, which is the pre-rule emission: the failure path leaves the
    * port exactly where it was rather than interposing a view on evidence nobody has (§4.6). */
  private[spoon] def fboundWildcardUse(r: CtTypeReference[?]): Boolean = TypeShape.of(r) match
    case TypeShape.Named(_, as) if as.nonEmpty =>
      val formals = typeDeclarationOf(r).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
      formals.zip(as).exists { (f, a) =>
        TypeShape.of(a).isInstanceOf[TypeShape.Wildcard] &&
          Option(f.getSuperclass).exists(b => mentionedTypeVarNames(b)(f.getSimpleName))
      }
    case _ => false

  /** `java.lang.Iterable<E>` as reached from `r`'s supertypes, and only where `E` is a type this
    * scope can WRITE — java's own enhanced-for lookup, with §4.6's honest decline. */
  private[spoon] def javaIterableSuper(r: CtTypeReference[?]): Option[CtTypeReference[?]] =
    def walk(ref: CtTypeReference[?], fuel: Int): Option[CtTypeReference[?]] =
      if ref == null || fuel <= 0 then scala.None
      else if ref.getQualifiedName == "java.lang.Iterable" then Some(ref)
      else
        val d = typeDeclarationOf(ref).orNull
        if d == null then scala.None
        else
          val ups = (d match { case c: CtClass[?] => Option(c.getSuperclass).toList; case _ => Nil }) ++
                    (d.getSuperInterfaces.asScala.toList)
          ups.iterator.map(walk(_, fuel - 1)).collectFirst { case Some(x) => x }
    walk(r, 6).filter(i => TypeShape.of(i).args match
      case List(el) => mentionedTypeVarNames(el).isEmpty
      case _        => false)

  private[spoon] def defineLocal(v: CtVariable[?], vt: TypeRepr): SymId =
    val key = "@" + methodId.raw + "$L$" + v.getSimpleName + "#" + posKey(v)
    val mut = v.isInstanceOf[CtLocalVariable[?]] && isReassigned(v)
    val id  = minter.define(key)(sid => Symbol(sid, v.getSimpleName, v.getSimpleName, Flags(isMutable = mut), methodId, vt))
    registerVar(v, id)
    id

  /** does the enclosing method body write to `v` after its declaration? (then it's a `var`). */
  private[spoon] def isReassigned(v: CtVariable[?]): Boolean =
    val scope = v.getParent(classOf[CtExecutable[?]])
    scope != null && writesToVar(scope, v.getSimpleName)

  private[spoon] def writesToVar(scope: CtElement, name: String): Boolean =
    val assigns = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]])).asScala
    val unaries = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtUnaryOperator[?]])).asScala
    assigns.exists { a =>
      a.getAssigned match { case w: CtVariableWrite[?] => w.getVariable.getSimpleName == name; case _ => false }
    } || unaries.exists { u =>
      import UnaryOperatorKind.*
      Set(POSTINC, POSTDEC, PREINC, PREDEC).contains(u.getKind) &&
        (u.getOperand match { case va: CtVariableAccess[?] => va.getVariable.getSimpleName == name; case _ => false })
    }

  /** Assignment to a member whose DECLARED (emitted) type is a bare TYPE PARAMETER, where Java's
    * view of the access was ERASED (a RAW-bounded type variable). Restates the unchecked step.
    * Guarded on the parameter's name resolving here and the value not already having that type. */
  private[spoon] def toDeclaredTypeParam(assigned: CtExpression[?], e: CtExpression[?], t: Term): Term =
    declaredTypeOf(assigned) match
      case Some(tp: CtTypeParameterReference) => toTypeParam(tp, e, t)
      case _                                  => t

  /** the DECLARED type of an assignment target — the field's / local's own declaration, not
    * Spoon's (possibly raw-erased) view of the access. The field path goes through
    * [[fieldDeclOf]]; the variable path wraps `getDeclaration` for the same noClasspath
    * reason — a local's declaration is absent when the enclosing method is external. */
  private[spoon] def declaredTypeOf(assigned: CtExpression[?]): Option[CtTypeReference[?]] =
    assigned match
      case fw: CtFieldWrite[?]    => fieldDeclOf(fw.getVariable).map(_.getType)
      case vw: CtVariableWrite[?] =>
        try Option(vw.getVariable.getDeclaration).map(_.getType)
        catch { case _: Throwable => None }
      case _                      => None

  /** cast `t` to the in-scope resolution of type parameter `tp`, unless it already has it. */
  private[spoon] def toTypeParam(tp: CtTypeParameterReference, e: CtExpression[?], t: Term): Term =
    resolveTypeParam(tp.getSimpleName) match
      case Some(inScope) if t.tpe != TypeRef(NoPrefix, inScope) =>
        val tr = TypeRef(NoPrefix, inScope)
        Tree.Typed(t, tt(tr, e), tr, originOf(e))
      case _ => t

  /** Java permits two implicit conversions Scala forbids: array covariance (`Sub[]` → a
    * `Super[]` slot) and `null` → a type parameter. Insert an explicit `asInstanceOf` so the
    * ported assignment/initializer type-checks. */
  private[spoon] val primRank = Map("byte" -> 1, "short" -> 2, "char" -> 2, "int" -> 3, "long" -> 4, "float" -> 5, "double" -> 6)

  /** Java's UNCHECKED generic conversion — a RAW-typed value converts to any instantiation
    * without a check. Raw uses render CONTEXT-dependently, so the same java type can render two
    * ways in two scopes; emit exactly the cast java performs implicitly. Gated to targets whose
    * type variables all resolve here (never synthesize a `?T` stub). */
  /** JS-G31 — a POLY EXPRESSION (JLS 15.2): a LAMBDA or a METHOD REFERENCE, typed by the slot it
    * fills, in both languages. A cast at such an argument would elaborate the literal to a
    * `scala.FunctionN` FIRST, then fail the cast — so the faithful emission is the literal AT
    * THE SLOT, never a cast (probed against scala 3.8.4 for every SAM-conversion shape). ONE
    * function: written twice before, the two copies disagreed (ENGINE-LIMITS F8). */
  private[spoon] def polyExpression(e: CtExpression[?]): Boolean =
    e.isInstanceOf[CtLambda[?]] || e.isInstanceOf[CtExecutableReferenceExpression[?, ?]]

  /** JS-G31's answer AT THE CALL — every POLY-EXPRESSION argument restored to what `expr`
    * produced, with any cast an argument arm added removed. Answered ONCE here rather than per
    * arm (six and growing). ARITY answered PER INDEX, never by declining the whole call: a
    * vararg-packed tail is answered INSIDE the array against the arguments it was built from. */
  private[spoon] def polyArgsUncast(argEs: List[CtExpression[?]], args: List[Term], at: Origin)
                            (using Obligations): List[Term] =
    Obligations.consult(JS.G(31), at) {
      val poly = argEs.zipWithIndex.collect { case (e, i) if polyExpression(e) => i }.toSet
      if poly.isEmpty then scala.None
      else if args.sizeIs == argEs.size then
        Some(args.zipWithIndex.map { (t, i) => if poly(i) then uncastAdded(t, argEs(i)) else t })
      else packedUncast(argEs, args, poly)
    }.getOrElse(args)

  /** …the OTHER half: a poly expression takes its type from the SLOT, and an OVERLOAD SET gives
    * scala no single slot to type a lambda literal from (javac resolves by argument SHAPE;
    * scalac types the literal FIRST — `E134`, probed at scala 3.8.4). Ascribes an ASCRIPTION,
    * never a CAST (polyExpression's refusal still stands — a cast would elaborate the literal
    * to a `Function0` first, then fail). Fires only when: the argument is a LAMBDA (a method
    * reference is excluded, handled by `TirEmitter.samAscribed`); the callee is OVERLOADED at
    * this arity with the slot naming no expected type ([[overloadedSamSlot]]); the target is
    * NAMEABLE HERE ([[tpNameableHere]]) and java wrote no cast of its own. Target is the
    * LAMBDA'S OWN type (same as [[samResultTpt]]), never the callee's re-derived formal. */
  private[spoon] def polyArgsAscribed(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                               args: List[Term]): List[Term] =
    if args.sizeIs != argEs.size then args
    else args.zipWithIndex.map { (t, i) =>
      samLambdaOf(argEs(i)) match
        case Some(l) if !t.isInstanceOf[Tree.Typed] && overloadedSamSlot(ex, argEs.size, i) =>
          val lt = l.getType
          if lt == null || !tpNameableHere(lt) then t
          else
            val r = tpe(lt)
            if r == NoType then t else Tree.Typed(t, tt(r, l), r, originOf(argEs(i)))
        case _ => t
    }

  /** the LAMBDA whose own type is the target this argument ascribes to — the argument itself,
    * or a BRANCH of a poly CONDITIONAL (JLS 15.25 pushes the target type through both branches,
    * ENGINE-LIMITS K30 face 3). Target ascribed on the WHOLE conditional, not each branch.
    * `polyExpression` deliberately NOT widened to match — different catalog population. */
  private[spoon] def samLambdaOf(e: CtExpression[?]): Option[CtLambda[?]] = e match
    case l: CtLambda[?]      => Some(l)
    case c: CtConditional[?] =>
      List(c.getThenExpression, c.getElseExpression).collectFirst { case l: CtLambda[?] => l }
    case _                   => scala.None

  /** is the callee overloaded at this arity, AND does the slot at argument `i` fail to give
    * scala an expected type — [[polyArgsAscribed]]'s whole decision, read off the declaring
    * type's ALL methods (not just declared, since java's overload set spans the hierarchy) by
    * QUALIFIED NAME at that index. Fires when the alternatives DISAGREE at `i`, or agree on a
    * TYPE VARIABLE the call has yet to infer (scala must resolve the overload by typing the
    * arguments first, unlike java which solves `T` from another slot). Unreadable declaration →
    * no alternatives, nothing ascribed (§4.6); `RuntimeException` only, so a deep model's
    * `StackOverflowError` is not swallowed. */
  private[spoon] def overloadedSamSlot(ex: CtExecutableReference[?], arity: Int, i: Int): Boolean =
    val alts: List[List[CtTypeReference[?]]] =
      try
        Option(ex.getDeclaringType).flatMap(d => Option(d.getTypeDeclaration)).toList.flatMap { ct =>
          val es: List[CtExecutable[?]] =
            if ex.isConstructor then ct match
              case cl: CtClass[?] => cl.getConstructors.asScala.toList
              case _              => Nil
            else ct.getAllMethods.asScala.toList.filter(_.getSimpleName == ex.getSimpleName)
          es.map(_.getParameters.asScala.toList.map(_.getType))
        }
      catch { case _: RuntimeException => Nil }
    val here = alts.filter(_.sizeIs == arity)
    val slots = here.flatMap(ps => Option(ps(i)))
    here.sizeIs > 1 &&
      (slots.map(_.getQualifiedName).distinct.sizeIs > 1 ||
       // …and spelled with the wildcard excluded because Spoon's `CtWildcardReference` EXTENDS
       // `CtTypeParameterReference`, so a bare `isInstanceOf` claims every `?` as a variable
       // (`mentionsNamedTypeVar` carries the same note). A java FORMAL cannot be a bare
       // wildcard, so this excludes nothing that exists — it keeps the test from reading as the
       // one that is wrong everywhere else in this file.
       slots.exists(s => s.isInstanceOf[CtTypeParameterReference] &&
                         !s.isInstanceOf[CtWildcardReference]))

  /** [[polyArgsUncast]] where a VARARG PACK has changed the arity: `args` is the fixed prefix
    * plus ONE term holding the variadic elements, built from the `argEs` tail in order. */
  private[spoon] def packedUncast(argEs: List[CtExpression[?]], args: List[Term],
                           poly: Set[Int]): Option[List[Term]] =
    val fixed = args.size - 1
    if fixed < 0 || argEs.sizeIs <= fixed then scala.None
    else
      val (headEs, restEs) = argEs.splitAt(fixed)
      val headTs = args.take(fixed).zipWithIndex.map { (t, i) => if poly(i) then uncastAdded(t, headEs(i)) else t }
      def elems(es: List[Term]): Option[List[Term]] =
        if es.sizeIs != restEs.size then scala.None
        else Some(es.zipWithIndex.map { (t, k) => if poly(fixed + k) then uncastAdded(t, restEs(k)) else t })
      // the two shapes `varargPack` materialises — a SPREAD or an array literal; a third declines
      val packed = args.last match
        case r: Tree.Repeated => elems(r.elems).map(es => r.copy(elems = es))
        case n: Tree.NewArray => n.init.flatMap(elems).map(es => n.copy(init = Some(es)))
        case _                => scala.None
      packed.map(headTs :+ _)

  /** the casts an ARGUMENT ARM added, removed; the ones the JAVA SOURCE wrote, kept. Unreadable
    * cast list DECLINES (not "java wrote none") — a term left as-is is at worst a cast too many.
    * `RuntimeException` only, so a `StackOverflowError` is not swallowed (CLAUDE.md §4.58). */
  private[spoon] def uncastAdded(t: Term, e: CtExpression[?]): Term =
    val own = try Some(e.getTypeCasts.size) catch { case _: RuntimeException => scala.None }
    def depth(x: Term): Int = x match
      case Tree.Typed(inner, _, _, _) => 1 + depth(inner)
      case _                          => 0
    def strip(x: Term, n: Int): Term =
      if n <= 0 then x
      else x match
        case Tree.Typed(inner, _, _, _) => strip(inner, n - 1)
        case other                      => other
    own.fold(t)(n => strip(t, depth(t) - n))

  /** THE FORMAL OF AN INHERITED CALLEE, with the ANCESTOR's type variables replaced by what THIS
    * class instantiated them with — `None` where nothing substitutes. Closes the gap where the
    * formal is literally an ancestor's own type variable (`isGenericUse` declines it, though
    * ENGINE-LIMITS G12's rule against resolving a callee's own variables does not apply — the
    * `extends` clause says what THIS class instantiated it as, same fact as `ParentSubst`,
    * CLAUDE.md §4.56). Keyed by (owner FQN, formal name), never by name alone. Does NOT
    * substitute a WILDCARD formal (`tpe` has no shape for it) — declines rather than misrenders. */
  /** how many `[]` a type reference carries — the ARITY half of `ENGINE-LIMITS.md` G26's
    * comparison, which is the one thing that decides whether a cast at an inherited formal is a
    * translation or a `ClassCastException`. */
  private[spoon] def arrayDims(tr: CtTypeReference[?]): Int = tr match
    case a: CtArrayTypeReference[?] => 1 + arrayDims(a.getComponentType)
    case _                          => 0

  private[spoon] def inheritedFormal(tr: CtTypeReference[?], fuel: Int = 6): Option[TypeRepr] =
    if fuel <= 0 then scala.None
    else TypeShape.of(tr) match
      // wildcard arm above variable — declines either way, deliberately (see doc above)
      case TypeShape.Wildcard(_, _, _) => scala.None
      case TypeShape.Variable(tv) =>
        for
          d     <- typeParamDeclOf(tv)
          owner <- (d.getParent match { case ct: CtType[?] => Some(ct.getQualifiedName); case _ => scala.None })
          if ancestorFqns.headOption.getOrElse(Set.empty).contains(owner)
          arg   <- inheritedByDecl.headOption.flatMap(_.get(owner -> tv.getSimpleName))
          r     <- (try Some(tpe(arg)) catch { case _: Throwable => scala.None })
        yield r
      case TypeShape.Arr(_, c) =>
        inheritedFormal(c, fuel - 1).map(e =>
          AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(e)))
      case TypeShape.Absent | TypeShape.Prim(_) => scala.None
      case s =>
        val as   = s.args
        val subs = as.map(a => inheritedFormal(a, fuel - 1))
        if as.isEmpty || subs.forall(_.isEmpty) then scala.None
        else
          try Some(AppliedType(TypeRef(NoPrefix, typeSym(s.ref)),
                               as.zip(subs).map((a, x) => x.getOrElse(tpe(a)))))
          catch { case _: Throwable => scala.None }

  private[spoon] def uncheckedGeneric(target: CtTypeReference[?], e: CtExpression[?], t: Term,
                               rawTarget: Boolean = true, ownScope: Boolean = true): Term =
    // the type the argument has WHERE IT STANDS ([[castType]], not `e.getType`) — a cast is
    // what moves it, and the pre-cast type mentions no raw generic at all
    val et = castType(e)
    // a CLASS LITERAL's Spoon type lies about raw-ness (`AddAction.class` types raw `Class`,
    // emits `classOf[AddAction]`); casting it would destroy the inference it feeds
    val classLit = e match
      case fr: CtFieldRead[?] => fr.getVariable.getSimpleName == "class"
      case _                  => false
    // a METHOD REFERENCE belongs with the lambda: both are poly expressions typed FROM the target
    val bad = classLit || polyExpression(e) || e.isInstanceOf[CtLiteral[?]] ||
      e.isInstanceOf[CtNewArray[?]] || e.isInstanceOf[CtConditional[?]]
    // …the INHERITED formal, which the gates below cannot reach: a formal written as an
    // ancestor's own type variable is not a `isGenericUse` at all, and `tpResolvable` answers
    // `false` for it because the variable is not in THIS class's scope. See [[inheritedFormal]]
    // — the `extends` clause resolves it, exactly and only for that case.
    //
    // A DIMENSION MISMATCH DECLINES rather than casts (ENGINE-LIMITS G26): a cast there would
    // make an arity defect COMPILE and throw at run time instead of a loud typer error (§3).
    // Computed ONCE, behind the two cheap tests, so the denominators do not move for nothing.
    val inherited =
      if target == null || et == null || bad then scala.None
      else if !mentionsRawGeneric(et) || arrayDims(target) != arrayDims(et) then scala.None
      else inheritedFormal(target)
    if target == null || et == null || bad then t
    else if inherited.isDefined then
      val ct = inherited.get
      Tree.Typed(t, tt(ct, e), ct, originOf(e))
    else if !isGenericUse(target) then t
    else if !(if ownScope then tpResolvable(target) else tpConcrete(target) || calleeBounded(target)) then t
    else if !mentionsRawGeneric(et) && !(rawTarget && mentionsRawGeneric(target)) then t
    else
      // a CALLEE's formal belongs to its own declaration, not the caller's inherited
      // instantiation — except for a callee this class DECLARES itself, whose formals are
      // written in ITS OWN variables (measured: restricting too narrowly gave 3->2, too wide 3->35)
      val ownCallee =
        Option(e.getParent(classOf[CtInvocation[?]]))
              .flatMap(inv => Option(inv.getExecutable.getDeclaringType))
              .exists(dt => !ancestorFqns.headOption.getOrElse(Set.empty).contains(dt.getQualifiedName))
      val savedOv = inOverridingMember
      if ownCallee then inOverridingMember = false
      val ct = try if ownScope then tpe(target) else tpBoundErased(target)
               finally inOverridingMember = savedOv
      Tree.Typed(t, tt(ct, e), ct, originOf(e))

  /** JS-G13's clause, as a function of the SLOT — java's array covariance (JLS 10.10) puts a
    * value of one array type where another is declared, and scala's `Array` is invariant.
    * Extracted so [[coerce]]/[[slotConsults]] read ONE predicate (ENGINE-LIMITS F8). */
  private[spoon] def arrayCovSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
    target != null && target.isInstanceOf[CtArrayTypeReference[?]] && et != null &&
      et.isInstanceOf[CtArrayTypeReference[?]] && target.getQualifiedName != et.getQualifiedName

  /** …the SAME question asked at the RENDERING, which a java-name test cannot see: java's own
    * ERASURE can collapse two array types into one (e.g. an F-bounded `<E> E[] getUniverse`),
    * so [[arrayCovSlot]] finds nothing to compare while the emitted `Array[E]` still disagrees
    * with `Array[Enum[?]]` (scala arrays are INVARIANT). `want` is HANDED IN, not re-looked-up
    * — a second `tpe(target)` moves the lowering denominators for nothing (measured, 1,675). */
  private[spoon] def arrayCovRendered(target: CtTypeReference[?], want: TypeRepr, t: Term): Boolean =
    target != null && target.isInstanceOf[CtArrayTypeReference[?]] &&
      isScalaArrayType(t.tpe) && isScalaArrayType(want) && want != t.tpe

  /** JS-G14's clause — a primitive at a reference slot is java autoboxing, and the boxing's
    * target is the WRAPPER rather than the (often erased) formal. See [[arrayCovSlot]] for why
    * this is a named predicate rather than an inline condition. */
  private[spoon] def boxingSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
    target != null && et != null && et.isPrimitive && !target.isPrimitive &&
      !target.isInstanceOf[CtTypeParameterReference] && !target.isInstanceOf[CtArrayTypeReference[?]]

  /** JS-G09's question at a slot — java's UNCHECKED CONVERSION (JLS 5.1.9), legal at a raw type,
    * no scala image but a cast. A SHAPE test, deliberately not [[uncheckedGeneric]]'s narrower
    * gate list, so a refused site still reports "the difference applies here". */
  private[spoon] def uncheckedSlot(target: CtTypeReference[?], et: CtTypeReference[?]): Boolean =
    target != null && et != null && isGenericUse(target) &&
      (mentionsRawGeneric(et) || mentionsRawGeneric(target))

  /** THE SLOT ROWS, consulted at every arm that has a slot — JS-G09, JS-G13, JS-G14. One
    * function, six call sites (`Differences.everySlot`, one JLS 5.2 conversion). Called from the
    * ARM, never [[coerce]] (unreached for a slot-less node, honest discharge). Reads [[castType]]
    * bare, same as `coerce` (ENGINE-LIMITS K17, CLAUDE.md §4.6). */
  private[spoon] def slotConsults(slots: List[(CtTypeReference[?], CtExpression[?])], at: Origin)
                          (using Obligations): Unit =
    val pairs = slots.map((tg, e) => (tg, castType(e)))
    Obligations.consult(JS.G(13), at)(Option.when(pairs.exists((tg, et) => arrayCovSlot(tg, et)))(()))
    Obligations.consult(JS.G(14), at)(Option.when(pairs.exists((tg, et) => boxingSlot(tg, et)))(()))
    Obligations.consult(JS.G(9),  at)(Option.when(pairs.exists((tg, et) => uncheckedSlot(tg, et)))(()))

  /** [[slotConsults]] reached from the DECLARATION dispatch — `fieldDef`'s one caller.
    *
    * A forwarder and not a second statement of the rule: the slot rows are decided in one
    * function whichever dispatch reached them, and this exists only because the three
    * predicates read `castType`, which is a `BodyTranslator` member. */
  private[spoon] def slotConsultsAt(slots: List[(CtTypeReference[?], CtExpression[?])], at: Origin)
                    (using Obligations): Unit = slotConsults(slots, at)

  /** the (formal, argument) pairs of a call — the slot list [[slotConsults]] wants. Empty where
    * arities disagree (same case `coerceArgs` declines). Formals read BARE — a `catch` here
    * would fabricate "this callee takes no parameters" (CLAUDE.md §4.6). */
  private[spoon] def argSlots(ex: CtExecutableReference[?], argEs: List[CtExpression[?]]):
      List[(CtTypeReference[?], CtExpression[?])] =
    val formals = ex.getParameters.asScala.toList
    if formals.sizeIs == argEs.size then formals.zip(argEs).filter(_._1 != null) else Nil

  private[spoon] def coerce(target: CtTypeReference[?], e: CtExpression[?], t: Term, arrayCov: Boolean = true,
                     tpToObject: Boolean = true, unchecked: Boolean = true): Term =
    val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
    // the type the COERCED TERM actually has — [[castType]], not `e.getType`, since `expr`
    // already folded java's own casts onto it (JLS 5.2, 5.3). Matters for `boxing` below, which
    // names the WRAPPER to convert to, not just whether to (ENGINE-LIMITS K17 face 3).
    val et     = castType(e)
    val narrowing = target.isPrimitive && et != null && et.isPrimitive &&
      primRank.get(target.getSimpleName).exists(tr => primRank.get(et.getSimpleName).exists(_ > tr))
    // a primitive flowing into a concrete REFERENCE slot (`Object`, `Number`, …) is Java
    // autoboxing — Scala won't box into every such position, so make it explicit.
    val boxing = boxingSlot(target, et)
    // a value erased to `Object` (a generic method's result) flowing into a more specific
    // slot — Java inserts an unchecked downcast; Scala needs it explicit.
    val downcast = et != null && et.getQualifiedName == "java.lang.Object" &&
      !target.isPrimitive && target.getQualifiedName != "java.lang.Object"
    // a boxed wrapper at a PRIMITIVE slot is java auto-UNBOXING (possibly with widening) — emit
    // the explicit `.xxxValue()`. Only a CROSS-type unbox needs this (same-type is Predef's job)
    if et != null && !et.isPrimitive && target.isPrimitive && wrapperOf.values.toSet(et.getQualifiedName)
      && wrapperOf.get(target.getSimpleName).exists(_ != et.getQualifiedName) then
      return unbox(t, et.getQualifiedName, target.getSimpleName, e)
    // a LOSSY WIDENING conversion (JLS 5.1.2) — scala's implicit int2float/long2float/long2double
    // are deprecated (precision loss); emit the explicit `.toFloat`/`.toDouble` instead
    if et != null && et.isPrimitive && target.isPrimitive then
      val pair = (et.getSimpleName, target.getSimpleName)
      val lossyTarget = pair match
        case ("int", "float") | ("long", "float")  => Some(("toFloat", "scala.Float"))
        case ("long", "double")                    => Some(("toDouble", "scala.Double"))
        case _                                     => scala.None
      lossyTarget.foreach { (method, resultFqn) =>
        val msym = minter.external(s"scala.${primName(et.getSimpleName)}#$method", method)
        return Tree.Select(t, msym, TypeRef(NoPrefix, minter.external(resultFqn, resultFqn.split('.').last)), originOf(e))
      }
    // a type-parameter value flowing into a genuinely-`Object` slot (a return/assignment/var-init
    // where the target type is really `java.lang.Object`, not an erased formal — call args are
    // handled by `typeParamToObject` off the DECLARED formal, so this stays off that path):
    // Java erases `T` to `Object`; Scala's unbounded `T <: Any` does not conform. Cast it.
    val tpObj = tpToObject && et != null && et.isInstanceOf[CtTypeParameterReference] &&
      target.getQualifiedName == "java.lang.Object"
    // box to the primitive's WRAPPER, not the (often Object-erased) formal — satisfies both an
    // erased `Object` slot and a real `Integer`/`Number` one. Hoisted ABOVE `cast` so
    // `arrayCovRendered` reuses this rendering rather than a second `tpe(target)` lowering.
    val ct = if boxing then boxedPrimitive(et.getSimpleName) else tpe(target)
    val cast =
      tpObj ||                                                                // T → Object (non-arg)
      (isNull && target.isInstanceOf[CtTypeParameterReference]) ||             // null → type param
      (arrayCov && (arrayCovSlot(target, et) ||                               // array covariance
                    arrayCovRendered(target, ct, t))) ||                      // …at the RENDERING
      narrowing ||                                                            // int → short/byte/char
      boxing ||                                                               // int → Object/Number
      downcast                                                                // Object → specific
    if cast then
      // a target naming an ANCESTOR's type variable is rendered through the `extends` clause
      // ([[uncheckedGeneric]]'s own fact, ENGINE-LIMITS G12) — else `tpe` renders a sentinel
      // `Array[?]`. Asked ONLY where a cast is really emitted, to avoid moving denominators for nothing.
      val cct = if mentionsAnyTypeVar(target) then inheritedFormal(target).getOrElse(ct) else ct
      Tree.Typed(t, tt(cct, e), cct, originOf(e))
    else if unchecked then
      // a CONDITIONAL's unchecked conversion belongs to its BRANCHES (java assigns each operand
      // to the target type separately, K30 face 3) — recurses through `coerce`, not
      // `uncheckedGeneric` directly, so each branch gets whatever conversion IT needs
      conditionalBranches(e, t) match
        case Some((c, i)) =>
          val th = coerce(target, c.getThenExpression, i.thenp, arrayCov, tpToObject, unchecked)
          val el = coerce(target, c.getElseExpression, i.elsep, arrayCov, tpToObject, unchecked)
          if (th ne i.thenp) || (el ne i.elsep) then i.copy(thenp = th, elsep = el) else t
        case None => uncheckedOf(target, e, t, ct)

    else t

  /** the conditional and the `If` it produced, when `t` really is that conditional's translation.
    * Both halves are checked: `expr` may have wrapped or replaced it, and rebuilding something
    * that is no longer an `If` would silently drop a branch. */
  private[spoon] def conditionalBranches(e: CtExpression[?], t: Term): Option[(CtConditional[?], Tree.If)] =
    (e, t) match
      case (c: CtConditional[?], i: Tree.If) => Some((c, i))
      case _                                 => None

  /** the unchecked-conversion decision for a NON-conditional expression — extracted only so the
    * branch recursion above reads as one case beside it. */
  private[spoon] def uncheckedOf(target: CtTypeReference[?], e: CtExpression[?], t: Term, ct: TypeRepr): Term =
      val u = uncheckedGeneric(target, e, t)
      // decided on the RENDERED types, not Spoon's — an erased receiver's Spoon type still says
      // `Array<K>` while the emitted term is `Array[Object]`, which only the TIR's erased type sees
      if (u ne t) || !tpAccessibleHere(target) || !uncheckedFrom(t.tpe, ct) then u
      else Tree.Typed(t, tt(ct, e), ct, originOf(e))

  private[spoon] val wrapperOf = Map(
    "byte" -> "java.lang.Byte", "short" -> "java.lang.Short", "char" -> "java.lang.Character",
    "int" -> "java.lang.Integer", "long" -> "java.lang.Long", "float" -> "java.lang.Float",
    "double" -> "java.lang.Double", "boolean" -> "java.lang.Boolean")
  private[spoon] val valueMethod = Map(
    "int" -> "intValue", "long" -> "longValue", "float" -> "floatValue", "double" -> "doubleValue",
    "short" -> "shortValue", "byte" -> "byteValue", "boolean" -> "booleanValue", "char" -> "charValue")
  /** `wrapper.<prim>Value()` — explicit unboxing to a primitive, plus the WIDENING beside it
    * where a shortcut would name a nonexistent member. Java's unboxing is TWO conversions (JLS
    * 5.1.8 then 5.1.2); collapsed to one call for the six `Number` wrappers (ENGINE-LIMITS K17
    * face 2). `Character`/`Boolean` are NOT `Number`s — emitted as two explicit steps instead.
    * @param from the wrapper's FQN — the SOURCE, known by both callers. */
  private[spoon] def unbox(t: Term, from: String, prim: String, e: CtElement): Term =
    def primT(p: String) = TypeRef(NoPrefix, minter.external("scala." + primName(p), p))
    // the wrapper's OWN primitive, and whether reaching `prim` from it needs a second step.
    val own    = wrapperOf.collectFirst { case (p, w) if w == from => p }.getOrElse(prim)
    val viaOwn = own != prim && (own == "char" || own == "boolean")
    val step   = if viaOwn then own else prim
    valueMethod.get(step) match
      case Some(vm) =>
        // owner deliberately left None for Number members (interning it would re-key every
        // downstream finding, measured). Two-step path keys on the WRAPPER instead — `charValue`
        // is not a `Number` member, and moving the existing key would re-key for no gain.
        val vsym = minter.external(if viaOwn then s"$from#$vm" else "java.lang.Number#" + vm, vm)
        val call = Tree.Apply(Tree.Select(t, vsym, NoType, originOf(e)), Nil, vsym, primT(step), originOf(e))
        if viaOwn then Tree.Typed(call, tt(primT(prim), e), primT(prim), originOf(e)) else call
      case None => t
  private[spoon] def boxedPrimitive(prim: String): TypeRepr =
    wrapperOf.get(prim) match
      case Some(fqn) => TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn)))
      case None      => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

  /** Java VARARGS at the CALL SITE. `T...` is emitted `Array[T]`, so a call passing elements
    * POSITIONALLY has to materialize the array java would build; an already-array or generic
    * component is left alone. Stops at the program's EDGE (ENGINE-LIMITS K6.5): an EXTERNAL
    * callee's `T...` is a class file scalac reads as REPEATED, so it gets `Tree.Repeated`
    * (emitted as elements, no spread syntax) instead of a pack. Ownership decided STRUCTURALLY
    * (§4.56) from the declaring type being a shadow, never from the name. */
  /** JS-G38's question, as a function of the vararg slot: does the argument ALREADY hold the
    * array java would otherwise build? Named because [[varargPack]] and [[callConsults]] both
    * ask it (ENGINE-LIMITS F8). Rules: the CAST wins where there is one (outermost first); a
    * PRIMITIVE array component must match exactly (java packs `int[]` at `Object...` into ONE
    * element, CLAUDE.md §4.4); a bare `null` IS the array; ARRAY DIMENSION decides the
    * reference case via `dims(arg) >= dims(comp) + 1` (java packs at `H[]...`, ENGINE-LIMITS G26). */
  /* …and every one of the three reads below is BARE, because `varargPack` — the TRANSLATION
     this predicate is about, which calls this very function — reads all three bare within ten
     lines: `arr.getComponentType` in its own `comp`, `e.getTypeCasts` in `expr`'s cast fold,
     `e.getType` through `ty`. A `catch` on the consult side of a value the translation reads
     unwrapped can only ever hide a divergence between the two, and each default was a
     statement rather than an absence: `getComponentType` failing answered *the components
     agree* (so the argument passes through and java's `new Object[]{ x }` is not built),
     `getTypeCasts` failing answered *the source wrote no cast*. `CLAUDE.md` §4.6. The `null`
     handling is unchanged — an absent `getType` is normal under `noClasspath` and is what the
     `collectFirst` declines on. */
  private[spoon] def varargHoldsArray(comp: CtTypeReference[?], e: CtExpression[?]): Boolean =
    def componentAgrees(arr: CtArrayTypeReference[?]): Boolean =
      val ac = arr.getComponentType
      if ac == null || comp == null then true
      else if ac.isPrimitive || comp.isPrimitive then ac.getQualifiedName == comp.getQualifiedName
      else arrayDims(arr) >= arrayDims(comp) + 1
    val casts = e.getTypeCasts.asScala.toList
    val own   = e.getType
    (casts :+ own).collectFirst { case a: CtArrayTypeReference[?] => a }.exists(componentAgrees) ||
      (e match { case lit: CtLiteral[?] => lit.getValue == null && casts.isEmpty; case _ => false })

  /** the callee's declared parameters, or `scala.None` where the declaration cannot be read
    * (CLAUDE.md §4.6), shared by [[varargPack]]/[[callConsults]] so they never disagree. At a
    * `CtNewClass` the parser SYNTHESISES a wrong declaration (§4.59) — Spoon's anonymous-subtype
    * constructor has no real parameter list — so the SUPERCLASS's constructor is read instead
    * (JLS 15.9.5.1), chosen by the ERASED parameter types the reference carries. */
  private[spoon] def declParams(ex: CtExecutableReference[?]): Option[List[CtParameter[?]]] =
    anonSuperCtor(ex).orElse(execDeclOf(ex))
          .map(_.getParameters.asScala.toList)

  /** the SUPERCLASS constructor an anonymous-class construction really invokes — see
    * [[declParams]]. Matches the erased signature FIRST, arity only where unambiguous (a
    * generic constructor's names don't meet under noClasspath erasure, JS-G18). */
  private[spoon] def anonSuperCtor(ex: CtExecutableReference[?]): Option[CtExecutable[?]] =
    val cands =
      for
        dt   <- Option(ex.getDeclaringType).toList
        decl <- Option(dt.getDeclaration).toList.collect { case c: CtType[?] if c.isAnonymous => c }
        sup  <- Option(decl.getSuperclass).toList
        supD <- Option(sup.getDeclaration).toList.collect { case c: CtClass[?] => c }
        ctor <- supD.getConstructors.asScala.toList
      yield ctor
    val want = ex.getParameters.asScala.toList.map(t => Option(t).map(_.getQualifiedName))
    def named = cands.filter(_.getParameters.asScala.toList
      .map(p => Option(p.getType).map(_.getQualifiedName)) == want)
    def arity = cands.filter(_.getParameters.size == want.size)
    named match
      case one :: Nil => Some(one)
      case _          => arity match
        case one :: Nil => Some(one)
        case _          => scala.None

  /** THE CALL ROWS, consulted at every call dispatch — JS-G18, JS-G32, JS-G37…G40, JS-G42.
    * Called from [[coerceArgs]] (the ONE function both `invocation`/`ctorCall` reach). Predicates
    * read off the REFERENCE/DECLARATION, not by re-running [[varargPack]]. */
  private[spoon] def callConsults(ex: CtExecutableReference[?], argEs: List[CtExpression[?]], at: Origin)
                          (using Obligations): Unit =
    // BARE, for [[argSlots]]' reason: `coerceArgsFixed` and `passedThrough` both read
    // `isExternalCallee` unwrapped, and `false` here is not "unknown" — it is *this callee is
    // one of ours*, which is the fact JS-G37 and JS-G39/G40 are the two sides of, so a swallowed
    // failure would move the consult from one row to its opposite (`CLAUDE.md` §4.6).
    val external = isExternalCallee(ex)
    val ps       = declParams(ex)
    val variadic = ps.exists(l => l.nonEmpty && l.last.isVarArgs)
    val comp     = ps.filter(_ => variadic).map(_.last.getType).collect {
      case a: CtArrayTypeReference[?] => a.getComponentType }.orNull
    val holds    = variadic && ps.exists(l => argEs.sizeIs == l.size) &&
      argEs.lastOption.exists(varargHoldsArray(comp, _))
    // JS-G18 — under `noClasspath` an executable REFERENCE erases its generic formals and the
    // DECLARATION does not, so an argument at an external callee is where the two readings meet.
    Obligations.consult(JS.G(18), at)(Option.when(external && argEs.nonEmpty)(()))
    // JS-G32 — a formal written in the CALLEE's own type variables, which are not in scope here.
    Obligations.consult(JS.G(32), at)(Option.when(
      ps.exists(_.exists(p => Option(p.getType).exists(f => mentionsAnyTypeVar(f) && !tpResolvable(f)))))(()))
    // JS-G37 — java materialised the array and an in-program callee's parameter is emitted
    // `Array[T]`, so the call has to materialise it too.
    Obligations.consult(JS.G(37), at)(Option.when(variadic && !external && !holds)(()))
    // JS-G38 — …and where the slot already holds one, re-packing it would build an array of one.
    Obligations.consult(JS.G(38), at)(Option.when(variadic && holds)(()))
    // JS-G39 — an EXTERNAL callee's `T...` is a class file's, which scalac reads as a REPEATED
    // parameter; JS-G40 is the two composed, which is java's own vararg-forwarding idiom.
    Obligations.consult(JS.G(39), at)(Option.when(variadic && external)(()))
    Obligations.consult(JS.G(40), at)(Option.when(variadic && external && holds)(()))
    // JS-G42 — the component's element type is not at the call site whenever the declared
    // component is not already a concrete type.
    Obligations.consult(JS.G(42), at)(Option.when(variadic && comp != null && !tpConcrete(comp))(()))

  private[spoon] def varargPack(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                         recvSubst: Map[String, CtTypeReference[?]]): Option[List[Term]] =
    val ps = declParams(ex)
    ps match
      case Some(l) if l.nonEmpty && l.last.isVarArgs =>
        val fixed = l.size - 1
        val comp = l.last.getType match
          case arr: CtArrayTypeReference[?] => arr.getComponentType
          case _                            => null
        // already an array in the vararg slot — passed THROUGH where the callee is ours, a
        // SPREAD at an external one (see `passThrough`). Component types must agree (java
        // packs a primitive array mismatch instead, CLAUDE.md §4.4); reference components pass
        val passesArray = argEs.sizeIs == l.size && varargHoldsArray(comp, argEs.last)
        // the vararg element type, in priority order: the DECLARED component when concrete
        // (preferred over argument inference — ENGINE-LIMITS §0/G1, erase USES never
        // DECLARATIONS, 94 errors otherwise); else the RECEIVER's instantiation for a known
        // receiver's own type variable (ENGINE-LIMITS G12); else inferred from the trailing
        // arguments' own type, only when they all agree on one concrete type
        val elemRef: Option[CtTypeReference[?]] =
          if comp != null && tpConcrete(comp) then Some(comp)
          else if comp != null && !comp.isInstanceOf[CtTypeParameterReference] then
            Some(comp)
          else if comp != null && recvSubst.contains(comp.getSimpleName) then
            Some(recvSubst(comp.getSimpleName))
          else
            val ts = argEs.drop(fixed).map(e => e.getType)
            Option.when(ts.nonEmpty && ts.forall(t => t != null && !t.isPrimitive && tpConcrete(t)) &&
                        ts.map(_.getQualifiedName).distinct.sizeIs == 1)(ts.head)
        // the declaring type is a SHADOW iff reconstructed from bytecode — one answer for
        // which side of the program's edge the CALLEE is on
        val external = isExternalCallee(ex)
        if comp == null || argEs.sizeIs < fixed then None
        else if passesArray then passedThrough(ex, argEs, external, recvSubst)
        else if elemRef.isEmpty then None
        else
          val (head, rest) = argEs.splitAt(fixed)
          val fixedTerms = head.zipWithIndex.map { (e, i) => coerce(l(i).getType, e, expr(e)) }
          // THE ELEMENT TYPE, with an ANCESTOR's type variables replaced (ENGINE-LIMITS G26) —
          // else `tpe` renders a `?H` sentinel. [[inheritedFormal]] is the SAME lookup the
          // inherited-formal cast uses; `scala.None` where nothing substitutes. Argument
          // INFERENCE remains refused here (measured worse, 81 -> 83, twice).
          val ct = inheritedFormal(elemRef.get).getOrElse(tpe(elemRef.get))
          val elems = rest.map(e => coerce(elemRef.get, e, expr(e)))
          val at = AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(ct))
          val o = argEs.headOption.map(originOf).getOrElse(Origin.synthetic)
          Some(fixedTerms :+ (
            if external then Tree.Repeated(elems, at, o)
            else Tree.NewArray(TypeTree(ct, o), Nil, Some(elems), at, o)))
      case _ => None

  /** java already holds the array and passes it WHOLE through the `T...` slot — the MIRROR of
    * the pack above (ENGINE-LIMITS K6.5). Callee OURS: `None`, ordinary argument list. Callee a
    * CLASS FILE: scalac reads `T...` as REPEATED and a bare array conforms as ONE element (a
    * silent `Object`-element bug, CLAUDE.md §4.4, or an uncounted compile error otherwise) — so
    * the array is SPREAD (`arr*`), which still ALIASES `arr` as java's pass-through does. */
  private[spoon] def passedThrough(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                            external: Boolean,
                            recvSubst: Map[String, CtTypeReference[?]]): Option[List[Term]] =
    if !external then None
    else
      // through `coerceArgsFixed`, never around it: the erasure cast a java `Object...` formal
      // needs (`args.asInstanceOf[Array[Object]]`) is that function's answer, and a second
      // spelling of it here would be a second answer.
      val terms = coerceArgsFixed(ex, argEs, recvSubst)
      if terms.sizeIs != argEs.size then None
      else Some(terms.init :+ Tree.Spread(terms.last, terms.last.tpe, originOf(argEs.last)))

  /** coerce each argument to its formal parameter type (Java autoboxing / numeric narrowing
    * that Scala won't do implicitly). Skipped when arities differ (varargs spread etc.). */
  private[spoon] def coerceArgs(ex: CtExecutableReference[?], argEs: List[CtExpression[?]], at: Origin,
                         recvSubst: Map[String, CtTypeReference[?]] = Map.empty)
                        (using Obligations): List[Term] =
    // the CALL dispatches' area-G consults, at the one function both `invocation`/`ctorCall` reach
    callConsults(ex, argEs, at)
    slotConsults(argSlots(ex, argEs), at)
    varargPack(ex, argEs, recvSubst).getOrElse(coerceArgsFixed(ex, argEs, recvSubst))

  /** the receiver's own type arguments, by the declaring class's parameter NAMES — `Graph<V>`
    * called on `DirectedGraph<Integer>` gives `V -> Integer`. Only a fully known instantiation
    * (same arity, every argument NAMEABLE HERE — not merely CONCRETE, `tpConcrete` excludes the
    * caller's own variable wrongly, [[tpNameableHere]] is the repair — no wildcards). */
  private[spoon] def receiverTypeArgs(inv: CtInvocation[?]): Map[String, CtTypeReference[?]] =
    val rt = inv.getTarget match
      case null => null
      case _: CtSuperAccess[?] | _: CtTypeAccess[?] => null
      case t    => castType(t)
    typeArgSubst(rt)

  /** what a REFERENCE's instantiation says the DECLARING type's formals are — `Bag<V>` says
    * `E := V`, by position. ONE derivation, read by [[receiverTypeArgs]] and by
    * [[nullToSamResult]] — Spoon's `TypeAdaptor` measurably does NOT answer this for a lambda
    * target, so it is derived here rather than asked of it. */
  private[spoon] def typeArgSubst(rt: CtTypeReference[?]): Map[String, CtTypeReference[?]] =
    if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]] ||
       rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then Map.empty
    else
      val formals = typeDeclarationOf(rt).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
      val actuals = rt.getActualTypeArguments.asScala.toList
      if formals.nonEmpty && actuals.sizeIs == formals.size &&
         actuals.forall(a => !a.isInstanceOf[CtWildcardReference] && tpNameableHere(a))
      then formals.map(_.getSimpleName).zip(actuals).toMap
      else Map.empty

  /** @param recvSubst the RECEIVER's own type arguments ([[receiverTypeArgs]]) — THREADED from
    *   [[coerceArgs]], never re-derived (F8). Only [[nullToTypeParam]] reads it, for G12's rule:
    *   a callee's own type variables do not resolve at the call site, but the CLASS's do. */
  private[spoon] def coerceArgsFixed(ex: CtExecutableReference[?], argEs: List[CtExpression[?]],
                              recvSubst: Map[String, CtTypeReference[?]] = Map.empty): List[Term] =
    // array covariance at call args DISABLED for OUR OWN methods (Spoon erases `T[]` to
    // `Object[]`, which would break the overloaded Scala method wanting invariant `Array[T]`) —
    // enabled only for EXTERNAL callees, whose real (class-file) formal genuinely is `Object[]`
    val external = isExternalCallee(ex)
    val formals = ex.getParameters.asScala.toList
    // Under noClasspath, an executable REFERENCE erases a generic formal `T` to `Object`, so
    // `coerce` sees `null → Object` (legal) and skips the cast — yet the emitted method keeps
    // the real `T`, where `null → T` fails. Consult the DECLARATION's un-erased formals to
    // recover the type parameter and cast the null there (`set(null.asInstanceOf[T])`).
    val declFormals: Int => Option[CtTypeReference[?]] =
      val ps = Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
      i => ps.flatMap(l => if i < l.size then Option(l(i)) else None)
    if formals.size == argEs.size then
      argEs.zipWithIndex.map { (e, i) =>
        val base = expr(e)
        val c = nullToTypeParam(e, declFormals(i), recvSubst,
          coerce(formals(i), e, base, arrayCov = external, tpToObject = false, unchecked = false))
        val o = typeParamToObject(e, declFormals(i), c)
        // Java's unchecked conversion at an argument, off the DECLARATION's formal (the
        // reference's is erased under noClasspath). `rawTarget = false`: a raw FORMAL belongs to
        // the callee's scope, where name-directed fill can resolve differently than here, so
        // only a raw ARGUMENT type drives this cast. Skipped when a coercion already fired.
        if o ne base then o
        else
          val a = arrayFormalCast(e, declFormals(i), o)
          if a ne o then a
          else declFormals(i).map(f => uncheckedGeneric(f, e, o, rawTarget = false, ownScope = false)).getOrElse(o)
      }
    else argEs.map(expr)

  /** `null` passed to a callee slot whose real (un-erased) formal is a type parameter — cast it
    * (`m(null)` → `m(null.asInstanceOf[T])`). Dominant case: a self-call in scope. Second case:
    * the RECEIVER's, G12's rule — the declaring CLASS's variables resolve through the receiver's
    * type arguments, tried FIRST (exact, keyed on the DECLARING CLASS's own formals, unlike the
    * name-based `resolveTypeParam`), and only for a variable the class declares (§4.56). */
  private[spoon] def nullToTypeParam(e: CtExpression[?], declFormal: Option[CtTypeReference[?]],
                              recvSubst: Map[String, CtTypeReference[?]], t: Term): Term =
    val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
    def classOwned(tp: CtTypeParameterReference): Boolean =
      Option(tp.getDeclaration).map(_.getParent).exists(_.isInstanceOf[CtType[?]])
    def cast(target: CtTypeReference[?]): Term = Tree.Typed(t, tt(tpe(target), e), tpe(target), originOf(e))
    declFormal match
      case Some(tp: CtTypeParameterReference) if isNull &&
        classOwned(tp) && recvSubst.get(tp.getSimpleName).exists(tpNameableHere) =>
        cast(recvSubst(tp.getSimpleName))
      // through the BARRIER-AWARE frame — *is this name WRITABLE here*, not just *does it
      // resolve* (`resolveTypeParam` sees every enclosing scope by name, including ones java
      // forbids naming, e.g. a `static` member and its class's, JLS 8.4.4). [[tpAccessibleHere]]
      // is used by every other cast this frontend builds — deliberately WEAKER than
      // `sameVarInScope`, which was tried and wrong in both directions (measured 0 -> 2 on two
      // ports, §5's narrowing-is-not-exempt).
      case Some(tp: CtTypeParameterReference) if isNull && tpAccessibleHere(tp) =>
        cast(tp)
      case _ => t

  /** An ARRAY argument whose emitted element type is not the declared formal's — java arrays
    * are COVARIANT with an erased generic element, scala's are INVARIANT. Faithful port is an
    * explicit `asInstanceOf` at the USE, never a widened DECLARATION (measured catastrophic,
    * see [[erasureOfFormal]]). Driven by the DECLARATION's formal, never the reference's erased
    * one. Gated on [[formalNameableHere]] so the cast never names an unwritable variable. */
  private[spoon] def arrayFormalCast(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
    declFormal match
      case Some(arr: CtArrayTypeReference[?]) if formalNameableHere(arr) && isScalaArrayType(t.tpe) =>
        val want = tpe(arr)
        if want == t.tpe || !isScalaArrayType(want) then t
        else Tree.Typed(t, tt(want, e), want, originOf(e))
      case _ => t

  /** A type-parameter-typed value flowing into a slot whose real formal is concretely
    * `java.lang.Object` (`Json.writeValue(String, Object, …)`): Java erases `T` to `Object`, but
    * Scala's unbounded `T <: Any` does NOT conform to `Object`. Cast (`resource.asInstanceOf[Object]`).
    * Gated on the DECLARED formal being Object — NOT a type parameter erased to Object — so we
    * never break our own `foo(x: T)` methods (whose real Scala signature keeps the invariant `T`). */
  private[spoon] def typeParamToObject(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
    val et = e.getType
    // A read through a WILDCARD-filled receiver is the other value Scala types as `Any`.
    // `for (Iterator iter = it.iterator(); …) append(iter.next())` reads a RAW `Iterator`, which
    // Java types as `Object`; we render the raw receiver `JavaIterator[?]`, so Scala's result is
    // the wildcard — weaker than `Object`, and rejected by an `Object` slot.
    val wildcardRead = e match
      case inv: CtInvocation[?] =>
        val rt = Option(inv.getTarget).map(_.getType).orNull
        rt != null && !rt.isPrimitive && isGenericUse(rt) && hasWildcard(tpe(rt))
      case _ => false
    // the THIRD value scala types wider than `Object` is one THIS FRONTEND made:
    // `execDef.anyForEquals` retypes `equals(Object)`'s parameter to `scala.Any`, so forwarding
    // it hands `Any` to an `Object` slot. Read off THIS FRONTEND's own record (§4.56), not the
    // java — the widening happened only at the DECLARATION.
    val anyDeclared = t match
      case Tree.Ident(s, _, _) => minter.infoOf(s) match
        case TypeRef(_, a) => minter.fullNameOf(a) == "scala.Any"
        case _             => false
      case _ => false
    declFormal match
      case Some(f) if !f.isInstanceOf[CtTypeParameterReference] && f.getQualifiedName == "java.lang.Object"
                   && ((et != null && et.isInstanceOf[CtTypeParameterReference]) || wildcardRead || anyDeclared) =>
        val obj = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))
        Tree.Typed(t, tt(obj, e), obj, originOf(e))
      case _ => t

  private[spoon] def tryStmt(t: CtTry, resources: List[Tree.ValDef])(using Obligations): Term =
    var multiCatch = false
    val catches = t.getCatchers.asScala.toList.map { c =>
      val p  = c.getParameter
      val pt = p.getMultiTypes.asScala.toList match
        case Nil    => tpe(p.getType)
        case multi  => multiCatch = true; multi.map(tpe).reduce(OrType(_, _))
      val id = defineLocal(p, pt)
      Tree.CatchCase(Tree.ValDef(id, tt(pt, p), None, originOf(p)), blockTerm(c.getBody))
    }
    // JS-S14 — java's multi-catch `A | B` has a scala image (a union type in the pattern), and
    // that image is built HERE. Fires where the source really wrote one.
    Obligations.consult(JS.S(14), originOf(t))(Option.when(multiCatch)(()))
    Tree.Try(resources, blockTerm(t.getBody), catches, Option(t.getFinalizer).map(blockTerm), unitT, originOf(t))

  /** Java switch → TIR `Match`. Empty (grouping) cases merge their labels into the next;
    * genuine fallthrough is lowered by TAIL DUPLICATION — a non-terminated case's body is
    * its own statements followed by the next case's closure (the same faithful lowering
    * the BIR frontend uses, RESEARCH §4.2), so no `Unsupported`. */
  private[spoon] def switchStmt(s: CtSwitch[?])(using Obligations): Term =
    val cases = s.getCases.asScala.toList
    val selT  = Option(s.getSelector.getType).map(tpe).getOrElse(NoType)
    val arms  = switchArms(cases, s, selT, unitT, isExpr = false)
    // java's switch with no `default` FALLS OUT; scala's `match` throws `MatchError` — add the
    // fall-out arm java has, except where [[isEnhanced]] says java does not fall out either
    val needsFallOut = !arms.exists(_.isDefault) && !isEnhanced(cases, s.getSelector)
    // JS-S05 — a `switch` with no `default` FALLS OUT when nothing matches; a `match` with no
    // `case _` throws `MatchError`, and falling out is often the NORMAL path (a scanner reading
    // an ordinary character). Fires exactly where the arm has to be synthesised — read off the
    // decision itself, so the consult cannot say something the code does not do.
    Obligations.consult(JS.S(5), originOf(s))(Option.when(needsFallOut)(()))
    val withDefault =
      if !needsFallOut then arms
      else arms :+ Tree.CaseDef(Nil, None, unit(s), isDefault = true)
    Tree.Match(expr(s.getSelector), withDefault, unitT, originOf(s))

  /** A SWITCH EXPRESSION — JLS 15.28, catalog `JS-S09`. `CtSwitchExpression` does NOT extend
    * `CtSwitch`, so the statement arm never caught it; `Tree.Match` already renders in either
    * position, so only the arms differ. THREE JLS rules: no fall-out arm (must be EXHAUSTIVE,
    * 15.28.1); an arm produces a VALUE (tail `yield` peeled into the result, others stay
    * [[Tree.Yield]] under a boundary); `yield` NOT unwrapped here (only [[caseBody]] undoes
    * Spoon's arrow-arm normalisation for statements). Fallthrough/break/label rules shared with
    * the statement form via [[switchArms]] (ENGINE-LIMITS F8). */
  private[spoon] def switchExpr(sw: CtSwitchExpression[?, ?])(using Obligations): Term =
    val resT = ty(sw)
    // JS-S09 — always fires: every switch expression needs the image, and choosing `Tree.Match`
    // for it is the whole content of the row.
    Obligations.consult(JS.S(9), originOf(sw))(Some(()))
    val cases = sw.getCases.asScala.toList
    val selT  = Option(sw.getSelector.getType).map(tpe).getOrElse(NoType)
    Tree.Match(expr(sw.getSelector), switchArms(cases, sw, selT, resT, isExpr = true), resT, originOf(sw),
               isExpr = true)

  /** the statements of one `case`, with Spoon's ARROW normalisation undone where java has no
    * such construct. Flattens an arrow-arm's `CtBlock`; unwraps an arrow STATEMENT arm's
    * synthetic `CtYieldStatement` (JLS 14.21: `yield` is legal only in a switch EXPRESSION). */
  private[spoon] def caseBody(c: CtCase[?], isExpr: Boolean): List[CtStatement] =
    val raw = c.getStatements.asScala.toList match
      case List(b: CtBlock[?]) => b.getStatements.asScala.toList
      case l                   => l
    if isExpr then raw
    else raw.map {
      case y: CtYieldStatement => y.getExpression match
        case st: CtStatement => st
        case _               => y
      case other => other
    }

  /** ONE java switch's arms, at either of its two positions.
    *
    * The fallthrough lowering, the labelled-vs-unlabelled break distinction and the empty-arm
    * label accumulation are the same rules for a statement and for an expression — java's
    * colon form falls through in both — so they are stated once. What the caller supplies is
    * how an arm's BODY becomes a term: a statement arm is a `Unit` block, an expression arm is
    * a block whose result is the arm's value. */
  private[spoon] def switchArms(cases: List[CtCase[?]], el: CtElement, selT: TypeRepr, resT: TypeRepr,
                         isExpr: Boolean)(using Obligations): List[Tree.CaseDef] =
    // per case: (body without a trailing break, terminated?)
    val split = cases.map { c =>
      val raw = caseBody(c, isExpr)
      // A trailing COMMENT is not a terminator. With comments enabled Spoon hands back a
      // free-floating `// …` as a statement of its own, and it can be the last one — reading
      // `last` literally would then miss the `break` behind it and fall the case through.
      raw.reverse.dropWhile(_.isInstanceOf[CtComment]) match
        // …an UNLABELLED one. `case '"': break outer;` does not end the case, it leaves the
        // enclosing LOOP; stripping it as a terminator silently deleted the jump, and the
        // quoted-string scanner in `JsonSkimmer` ran off the end of every string.
        case (b: CtBreak) :: _ if b.getTargetLabel == null => (raw.filterNot(_ eq b), true)
        // An ARROW arm NEVER falls through — JLS 14.11.2 gives the arrow form exactly one
        // statement group and no fallthrough at all, which is the whole reason SE14 added it.
        // Read off the CASE KIND and not off the body: an arrow arm's body carries no
        // terminator to find, so a rule that only looked for one would duplicate the NEXT
        // arm's tail into every arrow arm in the switch.
        case rest => (raw, c.getCaseKind == CaseKind.ARROW || rest.headOption.exists {
          // …and a `yield` terminates a colon-form EXPRESSION arm, exactly as a `return` and a
          // `throw` terminate a statement one: JLS 14.21 completes the whole switch expression
          // abruptly, so nothing after it in the next case can be reached from here.
          case _: CtReturn[?] | _: CtThrow | _: CtYieldStatement => true
          case _                                                 => false
        })
    }
    // JS-S07 — only an UNLABELLED trailing `break` terminates a case; a labelled one leaves the
    // enclosing LOOP, and stripping it as a terminator deletes the jump. Read off the split that
    // has just been taken, so the consult cannot say something the code does not do. It fires
    // where a case really ended on a bare `break`, which is the shape the distinction is about.
    Obligations.consult(JS.S(7), originOf(el))(
      Option.when(cases.zip(split).exists { (c, sp) => sp._2 && sp._1.size != caseBody(c, isExpr).size })(()))
    val closures = new Array[List[CtStatement]](cases.length)
    for i <- cases.indices.reverse do
      val (body, terminated) = split(i)
      closures(i) = if terminated || i == cases.length - 1 then body else body ++ closures(i + 1)
    // JS-S04 — java's switch FALLS THROUGH into the next case's statements and a `match` arm
    // never does, so a non-terminated arm is lowered by DUPLICATING the next case's tail into
    // it. Fires where an arm really runs on: a case that is neither terminated nor last and has
    // statements of its own.
    Obligations.consult(JS.S(4), originOf(el))(
      Option.when(cases.indices.exists(i =>
        !split(i)._2 && i != cases.length - 1 && split(i)._1.nonEmpty))(()))
    // JS-S10 — a PATTERN case label (JLS 14.11.1). Consulted at every switch and fired where one
    // is really written, and stated HERE rather than inside `caseLabel` for two reasons: the
    // obligation is owed at the two switch kinds' dispatches, which is where the scope is; and
    // `caseLabel` has two arms — the lowered type pattern and the refused record one — so a
    // consult written in each would be the F8 shape, one rule with two copies.
    Obligations.consult(JS.S(10), originOf(el))(
      Option.when(cases.exists(_.getCaseExpressions.asScala.exists(_.isInstanceOf[CtCasePattern])))(()))
    val out     = List.newBuilder[Tree.CaseDef]
    var pending = List.empty[Term]
    cases.zipWithIndex.foreach { case (c, idx) =>
      val labels    = c.getCaseExpressions.asScala.toList.map(caseLabel(_, c, selT))
      // `case null, default ->` (JLS 14.11.1) is ONE case that is both a null label and the
      // default. Read from `getIncludesDefault` rather than from an empty label list, or the
      // arm would render `case null` and leave the switch without the default java wrote.
      val isDefault = labels.isEmpty || c.getIncludesDefault
      val isLast    = idx == cases.length - 1
      if split(idx)._1.isEmpty && caseBody(c, isExpr).isEmpty && !isDefault && !isLast then pending = pending ++ labels
      else
        // …through `blockOf`, so an arm that ENDS on a comment keeps it. This is where the
        // shape is MANUFACTURED as often as it is written: the case-terminator `break` is
        // deleted above, and a comment written above that break becomes the arm's last
        // statement the moment it goes.
        val body =
          if isExpr then armValue(closures(idx), c, resT) else blockOf(closures(idx), c)
        out += Tree.CaseDef(pending ++ labels, Option(c.getGuard).map(expr), body, isDefault)
        pending = Nil
    }
    out.result()

  /** one case LABEL — the SPLIT `JS-S10` is about. A TYPE PATTERN (JLS 14.11.1) lowers exactly
    * to `Tree.TypePattern` + `CaseDef.guard`. A RECORD pattern too (`JS-C43`'s derived
    * `unapply`, see [[recordPattern]]). An UNNAMED pattern stays refused (no source Spoon 11.5
    * builds one, ENGINE-LIMITS T19). MARKER minted HERE, not via `expr`'s default, since
    * `CtCasePattern` carries no source POSITION (falls back to the unit-fatal throw otherwise) —
    * carries the SELECTOR's type, not the pattern's own `java.lang.Void`. The binding is an
    * ordinary local: probed, its `CtLocalVariable` carries its own valid position even though
    * the wrapper does not, so two same-named arms intern as two symbols correctly. */
  private[spoon] def caseLabel(e: CtExpression[?], c: CtCase[?], selT: TypeRepr): Term = e match
    case cp: CtCasePattern => cp.getPattern match
      case tp: CtTypePattern =>
        val v  = tp.getVariable
        val vt = tpe(v.getType)
        Tree.TypePattern(defineLocal(v, vt), tt(vt, v), vt, originOf(c))
      case rp: CtRecordPattern => recordPattern(rp, c, selT)
      case other =>
        unlowered(c, s"a pattern case label — ${SpoonKinds.nameOf(other.getClass)} " +
          "(JLS 14.11.1). No source this parser accepts builds one, so this refusal is a " +
          "claim about a node that has never been handed over (ENGINE-LIMITS T19)",
          selT, about = other)
    case other => expr(other)

  /** `case Point(int x, int y) ->` — java's RECORD PATTERN, as scala's constructor pattern. THE
    * ONE DISTINCTION: JLS 14.30.2's UNCONDITIONAL component pattern matches `null`, a narrowing
    * one (`Tree.TypePattern`) does not — scala needs `Tree.BindPattern` for the first. Asked of
    * SPOON's `isSubtypeOf` (JLS 4.10), narrowing arm taken where it cannot answer. A component
    * that is neither shape is refused IN PLACE. The RECORD ITSELF must be one this run LOWERS —
    * `JS-C43`'s derived `unapply` names nothing for a dependency's record — decided
    * STRUCTURALLY (does this parse hold a `CtRecord` declaration, §4.56), never by name. */
  private[spoon] def recordPattern(rp: CtRecordPattern, c: CtCase[?], selT: TypeRepr): Term =
    val rt   = tpe(rp.getRecordType)
    val at   = originOf(c)
    // the DECLARATION the pattern names, if this parse has one — the licence for the extractor
    // and, in its `getRecordComponents`, java's own answer to "is this pattern unconditional".
    val decl = Option(rp.getRecordType).flatMap(r => Option(r.getTypeDeclaration)).collect {
      case r: CtRecord => r
    }
    if decl.isEmpty then
      return unlowered(c, "a RECORD PATTERN over a record this run does not model (JLS 14.30.1). " +
        "The extractor a record pattern deconstructs through is DERIVED — JS-C43 writes an " +
        "`unapply` over the accessors into every record this run emits — and scala derives none " +
        "for a java record read out of a class file, so a pattern over one from a dependency " +
        "would name nothing", selT, about = rp)
    val comps = decl.toList
      .flatMap(_.getRecordComponents.asScala.toList.sortBy(posKey).map(_.getType))
    val subs = rp.getPatternList.asScala.toList.zipWithIndex.map { (p, k) =>
      p match
        case tp: CtTypePattern =>
          val v  = tp.getVariable
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt)
          if unconditional(comps.lift(k), v.getType) then Tree.BindPattern(id, vt, at)
          else Tree.TypePattern(id, tt(vt, v), vt, at)
        case nested: CtRecordPattern => recordPattern(nested, c, selT)
        case other =>
          unlowered(c, s"a record-pattern COMPONENT — ${SpoonKinds.nameOf(other.getClass)} " +
            "(JLS 14.30.1)", tpe(rp.getRecordType), about = other)
    }
    Tree.RecordPattern(tt(rt, rp), subs, rt, at)

  /** is a component pattern UNCONDITIONAL — does its type already cover the component's (JLS
    * 14.30.2)? `false` where the component's type is unknown, which is the narrowing arm and the
    * conservative side. */
  private[spoon] def unconditional(component: Option[CtTypeReference[?]], pattern: CtTypeReference[?]): Boolean =
    component.exists(ct => ct == pattern || ct.isSubtypeOf(pattern))

  /** one switch-EXPRESSION arm's statements as a term whose VALUE is the arm's.
    *
    * The last statement is the arm's result, and where it is a `yield` the node is peeled: a
    * tail `yield` is what a scala arm already means, so carrying it would make every arm need a
    * boundary it does not want. Everything else is left exactly as translated — a `Throw`, or
    * an `if` whose branches all jump, is `Nothing` in scala and conforms wherever the switch's
    * type is used, which is java's own definite-completion rule (JLS 15.28.1) doing the work. */
  private[spoon] def armValue(ss: List[CtStatement], el: CtElement, resT: TypeRepr): Term =
    val (sts, trail)  = stmts(ss)
    val (init, value) = sts.lastOption match
      case Some(t: Term) => (sts.init, unYield(t))
      case _             => (sts, unit(el))
    Tree.Block(init, value, resT, originOf(el), trail)

  /** peel a TAIL `yield` to the value it carries — through a comment wrapper, which is where
    * the trivia harvest puts an arm's own comments. */
  private[spoon] def unYield(t: Term): Term = t match
    case y: Tree.Yield     => y.value
    case c: Tree.Commented => c.stmt match
      case y: Tree.Yield => c.copy(stmt = y.value)
      case _             => t
    case _                 => t

  /** is this an ENHANCED switch STATEMENT — one java requires EXHAUSTIVE (JLS 14.11.2), so it
    * does NOT fall out? Asks BOTH of 14.11.2's disjuncts: the LABEL shape (a pattern/`null`), and
    * the SELECTOR'S TYPE (a QUALIFIED ENUM CONSTANT betrays nothing in the label list, JEP 441 —
    * javac compiles a `MatchException` throw where a naive read would answer classic). Deciding
    * from the label alone is WRONG (measured against javac). `noClasspath` unresolvable →
    * `false` (§4.6, the pre-existing behaviour). Both throw where it fires, different classes. */
  private[spoon] def isEnhanced(cases: List[CtCase[?]], selector: CtExpression[?]): Boolean =
    cases.exists(_.getCaseExpressions.asScala.exists {
      case _: CtCasePattern      => true
      case l: CtLiteral[?]       => l.getValue == null
      case _                     => false
    }) || selectorOutsideClassicSet(selector)

  /** JLS 14.11.2's classic selector set, by qualified name. A selector typed as one of these is
    * a classic switch however its labels are spelled; an ENUM is the set's sixth member and is
    * asked structurally below, because there is no name to list. */
  private[spoon] val ClassicSelectorTypes = Set(
    "char", "byte", "short", "int",
    "java.lang.Character", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
    "java.lang.String")

  /** does the selector's type PROVABLY resolve to something outside [[ClassicSelectorTypes]]?
    *
    * `false` is the answer for everything this cannot see — an absent type, a name that is not
    * in the set but whose declaration does not resolve, a type parameter, an annotation type —
    * and that default is the pre-existing behaviour rather than a fabricated fact (§4.6): it
    * says *this switch keeps the fall-out arm java's classic form has*, which is what every
    * switch in this engine's corpora got before the question was asked at all. The one lookup
    * wrapped is the RESOLUTION, where an absent value is normal under `noClasspath`. */
  private[spoon] def selectorOutsideClassicSet(selector: CtExpression[?]): Boolean =
    val ref = try Option(selector.getType) catch { case _: Throwable => None }
    ref.exists { r =>
      !r.isPrimitive && !ClassicSelectorTypes.contains(r.getQualifiedName) && {
        val decl = typeDeclarationOf(r)
        decl.exists {
          case _: CtEnum[?]                       => false
          case _: CtClass[?] | _: CtInterface[?]  => true
          case _                                  => false
        }
      }
    }

