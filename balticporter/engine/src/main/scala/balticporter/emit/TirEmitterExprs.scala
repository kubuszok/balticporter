package balticporter.emit

import balticporter.catalog.{CatalogLog, JS, Obligations, Rendering, Typing}
import balticporter.core.{EngineInfo, Provenance, Substituted}
import balticporter.tir.*

/** Term/expression rendering, try/match/block statement rendering, and final type/constant rendering split out of TirEmitter (context diet S1). */
private[emit] trait TirEmitterExprs:
  self: TirEmitter =>

  // ---- terms ----
  /** true when an `Ident`'s symbol is actually a TYPE used as a value (a static-access
    * receiver like `Float.compare`) — those must render as the (qualified) type name. */
  private[emit] def isTypeRef(id: SymId): Boolean = program.definitionOf(id) match
    case Some(_: Tree.ClassDef) => true
    case Some(_)                => false
    case None                   => val f = sym(id).fullName; f.contains('.') && !f.contains('#') && sym(id).info == TypeRepr.NoType

  /** a type used as a VALUE (static-access receiver) — dotted path, never a `#` projection
    * (which is type-position-only syntax). */
  private[emit] def typeValue(id: SymId): String =
    val s = sym(id)
    // a static nested type lives in the companion `object`, so name it through the value path
    // `Outer.Inner` even from inside `Outer` (companion members aren't in the class's scope).
    if s.flags.isStatic && s.fullName.contains('$') then escPath(s.fullName).replace('$', '.')
    else if currentDeclared(id) || inheritedNested(s.owner) then esc(s.name)
    else escPath(s.fullName).replace('$', '.')

  /** Is the given symbol an anonymous class? Decided from the symbol's `<anon>` NAME (the frontend
    * creates anonymous class symbols with `name = "<anon>"`), never from the `$NNN` suffix in its
    * `fullName` — §4.56: decide from the symbol's anonymous flag, not from a string pattern. */
  private[emit] def isAnonOwner(id: SymId): Boolean =
    id != SymId.None && program.symbolOf(id).exists(_.name == "<anon>")

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified.
    * An ANONYMOUS CLASS has no nameable path (its FQN's numeric suffix becomes a syntax error
    * after package rename), so its members render bare, decided from the `<anon>` name (§4.56). */
  private[emit] def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && !isAnonOwner(sm.owner) &&
       program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else if shadowedByCompanionStatic(s) then s"this.${esc(sm.name)}"
    else local(s)

  /** Does a bare reference to this INSTANCE member collide with a static of the same name the
    * enclosing companion carries or re-exports? Java has one namespace for statics and instance
    * fields (the inherited instance field simply wins); scala reports the bare name as ambiguous.
    * `this.` says what java meant. Decided from the TIR symbol, not the frontend (which cannot see
    * it under noClasspath). */
  private[emit] def shadowedByCompanionStatic(s: SymId): Boolean =
    val sm = sym(s)
    !sm.flags.isStatic && sm.owner != SymId.None && sm.info != TypeRepr.NoType &&
      !sm.info.isInstanceOf[TypeRepr.MethodType] && !sm.info.isInstanceOf[TypeRepr.PolyType] &&
      classStack.lastOption.exists { cur =>
        // an INHERITED member (declaring it here would shadow the static on its own)
        cur != sm.owner && ancestorsOf(cur).contains(sm.owner) &&
          classStack.exists(c => staticOwnersOf(c).contains(esc(sm.name)))
      }

  /** THE TERM RENDERING DISPATCH — the other half of §2.3(c)'s emitter surface. Not disjoint from
    * [[stat]]: a Term reached as a statement is handed straight here, so every consult happens in
    * the inner scope, joined by NODE IDENTITY rather than kind or origin. */
  private[emit] def term(t: Term, i: Int): String =
    Rendering.of(TirKinds.of(t), t.origin, t)(termArm(t, i))

  private[emit] def termArm(t: Term, i: Int)(using Obligations): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case l @ Tree.Literal(c, _, _)      =>
      Obligations.consult(JS.E(20), l.origin)(primitiveClassLiteral(c)).getOrElse(constant(c))
    case Tree.This(s, _, _)             => thisRef(s)
    case Tree.Super(_, _, _)            => "super"
    // A RECEIVER IS AN OPERAND: `.m` binds tighter than every control-flow expression and than
    // every operator, so `(c ? a : b).toString()` rendered from `term` reads
    // `if (c) a else b.toString()` — which parses, and calls the method on ONE BRANCH.
    case Tree.Select(q, s, _, _)        => s"${operand(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _, anon)      => s"new ${ctorTpe(tpt.tpe)}${anonBody(anon, i)}"
    case a @ Tree.Apply(fun, args, _, _, _) =>
      // JS-C06 — anInstance.staticMethod() evaluates the receiver for its side effects and
      // discards it; a companion call has no receiver slot to keep that evaluation in.
      Obligations.consult(JS.C(6), a.origin)(Option.when(fun match
        case Tree.Select(recv, m, _, _) => staticThroughInstance(recv, m)
        case _                          => false)(()))
      // JS-C22/C23 — java resolves an overload in THREE PHASES and scala in ONE; the decision here
      // is to render the call AS JAVA WROTE IT rather than model a resolver (ENGINE-LIMITS T17).
      // Two rows for JLS 15.12.2's two clauses: the phases, and the most-specific tie-break.
      locally {
        // the ENCLOSING type is java's candidate set (OverloadRiskCheck.rootOf), needed so the
        // emitter's consult and the check's count cannot disagree about which calls they cover.
        val risks = balticporter.tir.OverloadRiskCheck
          .risks(a, overloads, classStack.lastOption.getOrElse(balticporter.tir.SymId.None))(using program)
        Obligations.consult(JS.C(22), a.origin)(
          Option.when(risks.exists(_.issue != balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
        Obligations.consult(JS.C(23), a.origin)(
          Option.when(risks.exists(_.issue == balticporter.tir.OverloadRiskCheck.Issue.GenericTieBreak))(()))
      }
      // JS-G39, the EMITTER half — an external callee's T... reads as a REPEATED parameter, so the
      // pack becomes the tail of the argument list. HERE because argTerms flattens the Tree.Repeated
      // node before the dispatch would ever see it, so an arm there could never be consulted.
      Obligations.consult(JS.G(39), a.origin)(
        Option.when(args.exists(_.isInstanceOf[Tree.Repeated]))(()))
      applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _, compoundOp) =>
      // F7 (CLAUDE.md §4.4, JLS 15.26.2): a COMPOUND ASSIGNMENT evaluates the lvalue ONCE; the
      // direct rendering evaluates it TWICE. Non-trivial lvalue subexpressions get bound to a
      // temporary; simple lvalues (ident/this/literal) keep the direct form.
      compoundOp match
        case Some((op, narrow)) if hasNonTrivialSubexpr(l) =>
          val (bindings, lv) = bindLvalue(l, i)
          val rhsStr = operand(r, i)
          val expr = s"$lv $op $rhsStr"
          val rhs = narrow.fold(expr)(nt => s"($expr).asInstanceOf[${tpe(nt)}]")
          s"{ ${bindings.mkString("; ")}; $lv = $rhs }"
        case Some((op, narrow)) =>
          // compound but simple lvalue: direct form, lhs rendered twice
          val expr = s"${term(l, i)} $op ${operand(r, i)}"
          val rhs = narrow.fold(expr)(nt => s"($expr).asInstanceOf[${tpe(nt)}]")
          s"${term(l, i)} = $rhs"
        case None =>
          s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _, tr) => block(stats, expr, tr, i)
    case lam @ Tree.Lambda(ps, body, _, _, _) =>
      val head = s"(${ps.map(param).mkString(", ")}) => "
      // JS-S21 — a java lambda BODY is a method body, so `return` is legal and means "leave the
      // lambda" (JLS 15.27.2); scala's lambda is an expression and rejects `return` outright. A
      // NESTED `def` restores java's meaning exactly (a `def`'s return cannot be captured by an
      // enclosing loop's `boundary`, unlike a `break`/`continue` would need to be — §4.4).
      Obligations.consult(JS.S(21), body.origin)(Option.when(returnsIn(body))(()))
      if !returnsIn(body) then head + term(body, i)
      else lambdaResultType(lam) match
        case Some(rt) =>
          lambdaSeq += 1
          val n = s"body$$$lambdaSeq"
          head + s"{ def $n(): $rt = ${term(body, i)}; $n() }"
        // REFUSED rather than guessed (ENGINE-LIMITS I9): the def needs the SAM method's result
        // type, and a source-written lambda carries no method to read it off. Counted by
        // OmissionCheck.unnameableLambdaReturn.
        case None => head + term(body, i)
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    // A cast ON A POLY EXPRESSION is an ASCRIPTION (`ENGINE-LIMITS.md` K17 face 1): javac's cast
    // there supplies the expected type without being a runtime cast; `asInstanceOf` on a literal
    // elaborates to `Function0` first and throws. `operand` parenthesises the lambda. A
    // METHOD-VALUE ASCRIPTION (`(recv.m: (A, B) => R)`) pins which overload scala binds
    // (`OverloadRiskCheck.AscribeJavacChoice`) — unambiguous since JAVA HAS NO METHOD TYPES.
    case ty @ Tree.Typed(e, tpt, _, _) if tpt.tpe.isInstanceOf[TypeRepr.MethodType] =>
      // discharged NOT FIRED: this node is neither JS-G34's intersection cast nor JS-E06's
      // unboxing conversion, so both answers are facts rather than defaults (catalog(undischarged)).
      Obligations.consult(JS.G(34), ty.origin)(scala.None)
      Obligations.consult(JS.E(6), ty.origin)(scala.None)
      s"(${operand(e, i)}: ${tpe(tpt.tpe)})"
    case ty @ Tree.Typed(e, tpt, _, _)  =>
      val target = castTarget(e, tpt.tpe)
      // JS-G34 — java's INTERSECTION cast (`(A & B) x`, JLS 4.9) becomes scala's `A & B`.
      Obligations.consult(JS.G(34), ty.origin)(Option.when(target.isInstanceOf[TypeRepr.AndType])(()))
      // JS-E06 — a cast to a PRIMITIVE over a WRAPPER of a different primitive is java's UNBOXING
      // CONVERSION (JLS 5.1.8+5.1.2); asInstanceOf is an assertion that throws instead.
      Obligations.consult(JS.E(6), ty.origin)(
        CastConversionCheck.crossTypeUnbox(ty)(using program).map(_ => ()))
      if polyOperand(e) then s"(${operand(e, i)}: ${tpe(target)})"
      else s"${operand(e, i)}.asInstanceOf[${tpe(target)}]" // Java cast
    // JS-G39 at the position argTerms does NOT reach — a Tree.Repeated outside an argument list
    // still stands for a sequence of its own.
    case r @ Tree.Repeated(es, _, _)    =>
      Obligations.consult(JS.G(39), r.origin)(Some(()))
      es.map(term(_, i)).mkString(", ")
    // `xs*` — CLAUDE.md §6's spread, never `: _*`. operand because `*` binds tighter than the
    // expression it spreads. JS-G40 — java forwards the array whole through an external T...
    // slot, where a bare array would conform as ONE element.
    case s @ Tree.Spread(e, _, _)       =>
      Obligations.consult(JS.G(40), s.origin)(Some(()))
      s"${operand(e, i)}*"
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _, lbl)    =>
      loopWithJumps(b, lbl, bd => s"while (${term(c, i)}) $bd", term(b, i))
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    // JS-G21 — java restricts instanceof to a REIFIABLE type; isInstanceOf tests the erased class
    // exactly as java's does. Partial for the OTHER half — SE16's pattern BINDING has no image.
    case io @ Tree.InstanceOf(e, tpt, _, _) =>
      Obligations.consult(JS.G(21), io.origin)(Some(()))
      s"${operand(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${operand(a, i)}(${term(idx, i)})"
    // JS-G17 — java's .length is a FIELD of the array and scala's is a method.
    case al @ Tree.ArrayLength(a, _, _) =>
      Obligations.consult(JS.G(17), al.origin)(Some(()))
      s"${operand(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        // scala.Array, fully qualified: a bare Array collides with libGDX's own com.badlogic.gdx.utils.Array.
        case Some(es) => s"scala.Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        // java's new T[a][b] sizes every dimension; scala's new Array takes only ONE, so a
        // multi-dimension allocation lowers to Array.ofDim[base](a, b).
        case None if dims.sizeIs > 1 => s"scala.Array.ofDim[${tpe(baseElem(el.tpe))}](${dims.map(term(_, i)).mkString(", ")})"
        case None     => s"new scala.Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _, lbl) =>
      val raw  = sym(b.symbol).name
      val name = esc(raw)
      // JS-S15 — java's enhanced-for evaluates the ITERABLE once; satisfied by construction (the
      // generator interpolates term(it, …) exactly once).
      Obligations.consult(JS.S(15), it.origin)(Some(()))
      // TWO independent reasons to re-bind into one alias (K7 + F16): the DECLARED TYPE may differ
      // from the iterable's element type, and the binding may be REASSIGNED, which scala's
      // generator val does not permit.
      val mutable = reassignsBinding(body, b.symbol)
      val kw      = if mutable then "var" else "val"
      // JS-S16 — the binding may be REASSIGNED or DECLARED at a supertype; scala's generator is a
      // val of the element's own type and permits neither.
      Obligations.consult(JS.S(16), b.origin)(Option.when(mutable || widenedBinding(b, it).isDefined)(()))
      // JS-G04 — a captured WILDCARD on iteration has no nameable type (java relates the element
      // and its collection as ONE capture; scala captures per use) — the same repair as JS-S16, at
      // the shape with no scala name at all (ENGINE-LIMITS K7).
      Obligations.consult(JS.G(4), b.origin)(Option.when(it.tpe match
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)(()))
      // JS-S26 — a return inside an enhanced-for body becomes a NON-LOCAL RETURN under .foreach
      // desugaring; the lowering avoids it by emitting a while loop instead.
      Obligations.consult(JS.S(26), body.origin)(Option.when(returnsIn(body))(()))
      // K9 — a JDK Iterable the pipeline LEFT in the java namespace has no scala foreach; emit
      // java's own desugaring (JLS 14.14.2) instead. Decided from the POST-PIPELINE type (§4.56):
      // a retyped type or runtime shim already has foreach, so only an external java.*/javax.*
      // type needs the protocol.
      val keptJdk = isKeptJdkIterable(it.tpe)
      val hasReturn = returnsIn(body)
      (widenedBinding(b, it), mutable, keptJdk, hasReturn) match
        case (None, false, false, false) => loopWithJumps(body, lbl, bd => s"for ($name <- ${term(it, i)}) $bd", term(body, i))
        case (_, _, true, _) =>
          // JLS 14.14.2's own desugaring: evaluate the iterable ONCE, obtain its iterator, loop
          // with hasNext()/next(); break/continue go through loopWithJumps as the `for` form does.
          val itVar = esc(s"$raw$$it")
          val widened = widenedBinding(b, it)
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val nextExpr = if widened.isDefined then s"$itVar.next().asInstanceOf[$decl]" else s"$itVar.next()"
          loopWithJumps(body, lbl,
            bd => s"{ val $itVar = ${term(it, i)}.iterator(); while ($itVar.hasNext()) { $kw $name: $decl = $nextExpr; $bd } }",
            term(body, i))
        case (_, _, _, true) =>
          // a return inside a for-each body: lower to a while loop to avoid the non-local return
          // .foreach desugaring would produce.
          // PARENS: decided from the CALLEE SYMBOL's declaration, not receiver ownership (§4.56) —
          // program.owns was wrong in both directions for the runtime shims and for a converted
          // iterator.
          val iterHeadSym = headSymOf(it.tpe).getOrElse(SymId.None)
          val iterHasParens = calleeHasParens(iterHeadSym, "iterator")
          val iterCall = if iterHasParens then ".iterator()" else ".iterator"
          // hasNext's arity follows iterator's — one protocol (java or scala) is consistent
          // throughout, and the iterator TYPE's own members may not be interned yet to look up.
          val hasNextCall = if iterHasParens then ".hasNext()" else ".hasNext"
          val itVar = esc(s"$raw$$it")
          val widened = widenedBinding(b, it)
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val nextExpr = if widened.isDefined then s"$itVar.next().asInstanceOf[$decl]" else s"$itVar.next()"
          loopWithJumps(body, lbl,
            bd => s"{ val $itVar = ${term(it, i)}$iterCall; while ($itVar$hasNextCall) { $kw $name: $decl = $nextExpr; $bd } }",
            term(body, i))
        case (widened, _, _, _) =>
          // the alias is INSIDE the loop body, re-bound each iteration outside any continue
          // boundary loopWithJumps adds — java's own semantics. Derive the fresh name from the RAW
          // one, not the escaped one: appending to an escaped keyword produces a non-identifier
          // (measured, 0 -> 3 on libGDX).
          val fresh = esc(s"$raw$$e")
          // the CAST belongs to the widening only: a reassignment-only rebind already yields the
          // declared type.
          val decl = widened.getOrElse(tpe(b.tpt.tpe))
          val rhs  = if widened.isDefined then s"$fresh.asInstanceOf[$decl]" else fresh
          loopWithJumps(body, lbl,
            bd => s"for ($fresh <- ${term(it, i)}) { $kw $name: $decl = $rhs; $bd }",
            term(body, i))
    case Tree.For(init, cond, upd, body, _, _, lbl) =>
      // the UPDATE must run on a continue too, so it sits OUTSIDE the per-iteration boundary.
      // ONE LINE joined by `;`: a comment here would swallow the rest of the loop header, so any
      // that reached this far is stripped rather than emitting a broken file.
      val is = init.map(flatStat).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val u  = upd.map(flatStat).mkString("; ")
      // JS-S17 — java's classic for scopes ForInit to the loop and runs UPDATE on a continue too;
      // while has neither clause, so both must be PLACED explicitly.
      Obligations.consult(JS.S(17), body.origin)(Option.when(init.nonEmpty || upd.nonEmpty)(()))
      // an EMPTY java body (`for (...; ...; i++) ;`) renders no `()` statement: scalac's E129
      val emptyBody = Tree.uncomment(body) match
        case Tree.Block(Nil, Tree.Literal(Constant.UnitC, _, _), _, _, _) => true
        case Tree.Literal(Constant.UnitC, _, _)                          => true
        case _                                                           => false
      loopWithJumps(body, lbl,
        bd => if emptyBody then s"{ $is; while ($c) { $u } }" else s"{ $is; while ($c) { $bd; $u } }", term(body, i))
    case t: Tree.Try                    => tryStr(t, i)
    case m: Tree.Match                  => matchStr(m, i)
    case mr @ Tree.MethodRef(q, s, mrT, _, referent) =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
      val isStaticRef = referent.isInstanceOf[Referent.Static]
      // JS-G43, the EMITTER half — five java forms share one syntax and each becomes a DIFFERENT
      // scala lambda, discriminated right here (isCtor, isStatic, the array element test below).
      Obligations.consult(JS.G(43), mr.origin)(Some(()))
      // JS-G17's third face — T[]::new is an IntFunction[T[]], not a no-arg supplier (a scala
      // array needs a LENGTH). Fires only at the array-constructor form.
      Obligations.consult(JS.G(17), mr.origin)(Option.when(isCtor && (q match
        case Left(tt) => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(_)) => sym(as).fullName == "scala.Array"
          case _                                                     => false
        case _ => false))(()))
      // JS-G33 — SAM CONVERSION eligibility, asked from every Left form (constructor, unbound
      // instance, static — the static form is now an explicit lambda too) and every Right form.
      Obligations.consult(JS.G(33), mr.origin)(Option.when(q match
        case Left(_) => true
        case Right(_) => true)(()))
      // JS-C52 — @FunctionalInterface governs eta-expansion warnings; the static arm now emits
      // explicit lambdas at EVERY arity to avoid the warning, so this fires at every static reference.
      Obligations.consult(JS.C(52), mr.origin)(Option.when(isStaticRef && !isCtor)(()))
      q match
        // an ARRAY constructor reference T[]::new is an IntFunction[T[]] (size) => new T[size].
        // Route through ctorTpe, which drops wildcard arguments the scala compiler would reject as
        // "type argument must be fully defined", erasing a wildcard element to Object.
        case Left(tt) if isCtor => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(el)) if sym(as).fullName == "scala.Array" =>
            val elem = el match
              case _: TypeRepr.TypeBounds => "java.lang.Object"
              case other                  => tpe(other)
            s"((size: scala.Int) => new scala.Array[$elem](size))"
          // an ordinary T::new takes THE CONSTRUCTOR'S OWN PARAMETERS (not just a nilary factory),
          // read off Tree.MethodRef.referent; parameters go un-annotated since a constructor
          // reference is a poly expression samAscribed's target types.
          case _ =>
            val ps = referent match
              case Referent.Instance(n) => (0 until n).map(k => s"a$k$$").toList
              case Referent.Static(_)   => Nil // a constructor is never static; JLS 8.8.3
            samAscribed(s"((${ps.mkString(", ")}) => new ${ctorTpe(tt.tpe)}(${ps.mkString(", ")}))",
                        mrT, tt.tpe)
        // `Type::method` is TWO java forms sharing one syntax: STATIC is `Type.method`; INSTANCE
        // is UNBOUND (receiver becomes the first parameter). At arity ZERO the qualified name is
        // not a function at all — scala 3 refuses to eta-expand a nullary method — so a nilary
        // static reference takes the lambda form (`ENGINE-LIMITS.md` G32); every other arity keeps the name.
        case Left(tt) if isStaticRef && referent == Referent.Static(0) =>
          samAscribed(s"(() => ${tpe(tt.tpe)}.${local(s)}())", mrT, tt.tpe)
        // a static method reference at NON-ZERO arity: bare name where the target SAM type carries
        // @FunctionalInterface (eta-expansion is warning-free), else an explicit lambda to avoid
        // the -Werror'd eta-expansion warning. An unreadable annotation set is treated as
        // UNANNOTATED — the safe direction is the lambda, never a bare name scalac might reject.
        case Left(tt) if isStaticRef && targetHasFunctionalInterface(mrT) =>
          s"${tpe(tt.tpe)}.${local(s)}"
        case Left(tt) if isStaticRef =>
          val arity = referent match { case Referent.Static(n) => n; case _ => 0 }
          val formals = methodParams(s)
          val named = formals.sizeIs == arity && !hasWildcardArg(tt.tpe)
          val ps = (0 until arity).map(k =>
            if named then s"a$k$$: ${tpe(formals(k))}" else s"a$k$$").mkString(", ")
          val as = (0 until arity).map(k => s"a$k$$").mkString(", ")
          samAscribed(s"(($ps) => ${tpe(tt.tpe)}.${local(s)}($as))", mrT, tt.tpe)
        case Left(tt) =>
          val self  = "self$"
          // ARITY is java's, off the node; parameter TYPES are the SYMBOL's, and the two can
          // disagree for an external member with no readable MethodType — where they disagree the
          // whole lambda goes un-annotated rather than half-annotated (same poly-expression rule).
          val arity = referent match
            case Referent.Instance(n) => n
            case Referent.Static(n)   => n // unreachable: the arm above took it
          val formals = methodParams(s)
          val named   = formals.sizeIs == arity && !hasWildcardArg(tt.tpe)
          val as      = (0 until arity).map(k => s"a$k$$").mkString(", ")
          // the receiver parameter is ANNOTATED only when the qualifier names a real type: a RAW
          // qualifier annotated with it makes the call return an unusable capture, since java's
          // reference takes its meaning entirely from the TARGET (e.g. Comparator.comparing).
          val recvT = if named then s": ${tpe(tt.tpe)}" else ""
          val extra = (0 until arity).toList.map(k =>
            if named then s"a$k$$: ${tpe(formals(k))}" else s"a$k$$")
          val ps    = (s"$self$recvT" :: extra).mkString(", ")
          samAscribed(s"(($ps) => $self.${local(s)}($as))", mrT, tt.tpe)
        case Right(e)           => s"${term(e, i)}.${local(s)}"
    // Java's break leaves the loop; scala.util.boundary/break is the faithful shape (§4.4). A
    // LABELLED break reaches it through Tree.Labeled or the loop's own label field.
    case Tree.Break(scala.None, _, _) if breakTarget.isDefined =>
      breakTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)" // another boundary sits inside
        case _       => "scala.util.boundary.break(())"
    case Tree.Break(Some(l), _, _) if labelBreak.contains(l) =>
      val n = labelBreak(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    // an unlabelled break with no boundary belongs to a SWITCH terminator, already stripped by the
    // frontend — one reaching here is unrecognised. Say WHICH (§4.45).
    case b @ Tree.Break(scala.None, _, _)   =>
      unrenderable("break", "no enclosing loop or switch, and the frontend did not recognise it as " +
        "a switch-case terminator", "give the enclosing construct a `boundary`, or teach the " +
        "frontend this jump's shape", b.origin, "/* break: no enclosing loop or switch */ ()")
    case b @ Tree.Break(Some(l), _, _)      =>
      unrenderable("break", s"labelled `break $l` whose label is not in scope at this point",
        s"the labelled statement `$l` needs a NAMED boundary (§4.4); check `Tree.Labeled` reached it",
        b.origin, s"/* break $l: label not in scope */ ()")
    // a NON-TAIL yield (JLS 14.21) leaves the switch expression's arm with this value; matchStr
    // has put a named, value-carrying boundary around the arm. A TAIL yield never reaches here.
    // `case String s ->` — a java TYPE PATTERN as a case label (JLS 14.11.1); scala's typed
    // pattern is the exact image.
    case Tree.TypePattern(b, tpt, _, _) => s"${local(b)}: ${tpe(tpt.tpe)}"
    // `case Point(x, y)` — java's RECORD PATTERN derives over the record's ACCESSORS (JLS
    // 14.30.1, JS-C43). Named through typeValue (the companion's value path, where unapply is).
    case Tree.RecordPattern(tpt, ps, _, _) =>
      val nm = headSymOf(tpt.tpe).map(typeValue).getOrElse(tpe(tpt.tpe))
      s"$nm(${ps.map(term(_, i)).mkString(", ")})"
    // an UNCONDITIONAL component: the binding alone (a type test would be a different program).
    case Tree.BindPattern(b, _, _)      => local(b)
    case Tree.Yield(v, _, _) if yieldTarget.isDefined =>
      s"scala.util.boundary.break(${term(v, i)})(using ${yieldTarget.get})"
    case y @ Tree.Yield(v, _, _) =>
      unrenderable("yield", "no enclosing switch EXPRESSION arm — a `yield` outside one is not a " +
        "java construct (JLS 14.21), so the tree was built by something other than the switch arm",
        "check that the node was minted by `SpoonTir`'s switch-expression arm; a tail `yield` must " +
        "be peeled into the arm's value rather than carried as a node",
        y.origin, s"/* yield: no enclosing switch expression */ ${term(v, i)}")
    case Tree.Continue(scala.None, _, _) if contTarget.isDefined =>
      contTarget.filter(_.nonEmpty) match
        case Some(n) => s"scala.util.boundary.break(())(using $n)"
        case _       => "scala.util.boundary.break(())"
    case Tree.Continue(Some(l), _, _) if labelCont.contains(l) =>
      val n = labelCont(l)
      if n.isEmpty then "scala.util.boundary.break(())" else s"scala.util.boundary.break(())(using $n)"
    case c @ Tree.Continue(scala.None, _, _) =>
      unrenderable("continue", "no enclosing loop",
        "the loop BODY needs a `boundary` (§4.4); check which construct swallowed it",
        c.origin, "/* continue: no enclosing loop */ ()")
    case c @ Tree.Continue(Some(l), _, _)    =>
      unrenderable("continue", s"labelled `continue $l` whose label is not in scope at this point",
        s"the labelled loop `$l` needs a NAMED boundary around its body (§4.4)",
        c.origin, s"/* continue $l: label not in scope */ ()")
    // `name: stmt` — java's label on a NON-loop statement; the boundary goes around the STATEMENT
    // (§4.4), always named since a labelled jump crosses nested loops and switches by definition.
    case Tree.Labeled(name, s, _, _) =>
      // JS-S02 — java's label sits on ANY statement (JLS 14.7); scala has no labelled statement,
      // so the image is a NAMED boundary. Fires only where something really breaks to the label.
      Obligations.consult(JS.S(2), s.origin)(Option.when(jumpsTo(s, name, brk = true))(()))
      if !jumpsTo(s, name, brk = true) then term(s, i) // a label nobody breaks to is not control flow
      else
        labelSeq += 1
        val n     = s"lbl$$$labelSeq"
        val saved = labelBreak.get(name)
        labelBreak(name) = n
        val inner =
          try term(s, i)
          finally saved match { case Some(v) => labelBreak(name) = v; case _ => labelBreak.remove(name) }
        s"scala.util.boundary { ($n: scala.util.boundary.Label[scala.Unit]) ?=> $inner }"
    case Tree.Assert(c, m, _, _)        => s"assert(${term(c, i)}${m.map(x => ", " + term(x, i)).getOrElse("")})"
    // java's POST-increment yields the value BEFORE the update; the temporary is what makes it exact.
    case Tree.IncDec(tgt, op, post, _, _) =>
      // F7 (CLAUDE.md §4.4, JLS 15.14.2/15.15.1): same lvalue-once rule as compound assignment.
      if hasNonTrivialSubexpr(tgt) then
        val (bindings, lv) = bindLvalue(tgt, i)
        val prefix = bindings.mkString("; ")
        if post then s"{ $prefix; val ${'$'}prev = $lv; $lv $op= 1; ${'$'}prev }"
        else s"{ $prefix; $lv $op= 1; $lv }"
      else
        if post then s"{ val ${'$'}prev = ${term(tgt, i)}; ${term(tgt, i)} $op= 1; ${'$'}prev }"
        else s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }"
    case Tree.DoWhile(b, c, _, _, lbl)  => // Scala 3 has no do-while
      // JS-S18 — scala 3 removed do-while, so the body is lifted into the condition instead.
      Obligations.consult(JS.S(18), b.origin)(Some(()))
      loopWithJumps(b, lbl, bd => s"while ({ $bd; ${term(c, i)} }) ()", term(b, i))
    case Tree.Synchronized(l, b, _, _)  => s"${term(l, i)}.synchronized ${term(b, i)}"
    // An EXPRESSION position, where a comment cannot be rendered safely: a `//` would comment out
    // the rest of the line and a `/* */` would sit in the middle of a term. The frontend only ever
    // wraps a STATEMENT (`SpoonTir.withTrivia`), and `stat` handles that case above, so this is
    // reached only if a phase moves a wrapped statement into an operand — the statement is emitted,
    // the comment is not, and `TriviaCheck` reports the loss rather than the file being broken.
    case Tree.Commented(_, s)           => term(s, i)
    // Ready-made Scala, with any HOLES rendered as terms. The closed form (`holes = Nil`) is
    // `raw` verbatim and no scan runs over it — see `Tree.Opaque`.
    case o: Tree.Opaque                 => o.spliced(h => spliceOperand(h, i))
    // THE MARKER (`DESIGN.md` §6.2/§6.4). A RESOLVED one renders as its inner and nothing else: a
    // phase answered it, and a record of work done is not a residue. An OPEN one never ships.
    case m: Tree.Unportable             => unportable(m, i)

  /** Render a marker. `Open` has two answers: best-effort (the approximation inside deterministic
    * comment fences, since a comment cannot change program shape) or, by default,
    * `scala.compiletime.error` — the loudest available answer, deliberately opposite
    * `unrenderable`'s default, since here the engine has nothing to say at all. Either way the
    * refusal is recorded as `Decision.Kind.Unrenderable`. */
  private[emit] def unportable(m: Tree.Unportable, i: Int): String =
    m.state match
      case MarkerState.Resolved(_, _) => term(m.inner, i)
      case MarkerState.Open =>
        val d = Decision(
          kind       = Decision.Kind.Unrenderable,
          subject    = currentOwnerSym,
          subjectFqn = if currentOwnerSym == SymId.None then currentUnitName else sym(currentOwnerSym).fullName,
          detail     = Map("construct" -> m.kind.label, "why" -> m.what,
            "action" -> m.kind.remedies.headOption.map(_.what).getOrElse("no remedy is recorded for this kind"))
            ++ m.diff.map(dd => "catalog" -> dd.toString),
          reason = Reason.Universal(s"unportable/${m.kind.slug}"),
          origin = m.origin,
        )
        emissionOf += d
        printedNotes += PorterNote.Printed(d.kind, d.subject, d.subjectFqn, currentUnitName)
        recordedMarkers += m
        if bestEffort then
          val (openF, closeF) = Tree.Unportable.fence(m)
          s"$openF ${term(m.inner, i)} $closeF"
        else
          val msg = PorterNote.safe(s"balticporter: ${m.kind.label}: ${m.what}; " +
            m.kind.remedies.headOption.map(_.render).getOrElse("") + s"; origin ${m.origin.javaPath}:${m.origin.line}")
          PorterNote.render(d, "").stripSuffix("\n") + " " +
            "scala.compiletime.error(\"" + escape(msg) + "\")"

  /** every OPEN marker this emitter RENDERED — the input to the best-effort banner, and the
    * emitter's own half of the marker inventory. A value this emitter owns, exactly like the source
    * map and for the same reason (§5.1). */
  def renderedMarkers: List[Tree.Unportable] = recordedMarkers.toList
  private[emit] val recordedMarkers = collection.mutable.ListBuffer.empty[Tree.Unportable]

  /** A Java constructor reference (`Foo::new`) is typed by the TARGET functional interface java
    * resolved, not by `Foo`. Emitted bare, `() => new Foo()` is a `Function0`, which SAM-converts
    * to ANY interface, making an overload set AMBIGUOUS where java's was not — the resolved target
    * is re-stated as an ascription, strictly guarded so this can only narrow, never mis-type. */

  /** Does the TARGET SAM type carry `@FunctionalInterface`? REFUTER polarity (§4.56): an
    * unreadable annotation set is treated as UNANNOTATED, the safe direction being the explicit
    * lambda nobody warns about. */
  private[emit] def targetHasFunctionalInterface(target: TypeRepr): Boolean =
    headSymOf(target).flatMap(program.symbolOf).exists(
      _.annotations.exists(_.tpe match
        case TypeRepr.TypeRef(_, a) => sym(a).fullName == "java.lang.FunctionalInterface"
        case _                     => false))

  private[emit] def samAscribed(fn: String, target: TypeRepr, ctor: TypeRepr): String =
    def headOf(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headOf(tc)
      case _                           => None
    def concrete(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, s)       => !sym(s).flags.isParam
      case TypeRepr.AppliedType(tc, as) => concrete(tc) && as.forall(arg)
      case _                            => false
    // a bare `?` is a legal type ARGUMENT (`PoolSupplier[Array[?]]`) though not a legal type
    def arg(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => true
      case other                                                 => concrete(other)
    val ok = concrete(target) && headOf(target) != headOf(ctor)
    if ok then s"($fn: ${tpe(target)})" else fn

  /** a STATIC member reached through an instance expression rather than through its own type. */
  private[emit] def staticThroughInstance(recv: Term, m: SymId): Boolean =
    val s = sym(m)
    s.flags.isStatic && s.owner != SymId.None && program.symbolOf(s.owner).isDefined && (recv match
      // already qualified by the owning TYPE — `Family.one(…)` — which is what we want to emit.
      case Tree.Ident(q, _, _)     => q != s.owner
      case Tree.Select(_, q, _, _) => q != s.owner
      case _                       => true)

  /** conservatively: can evaluating this term have an effect? Only shapes that provably cannot are
    * treated as free, because being wrong in the other direction DROPS an effect. */
  private[emit] def effectFree(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This | _: Tree.Literal => true
    case Tree.Select(q, _, _, _)                        => effectFree(q)
    case _                                              => false

  // -- F7 lvalue binding (CLAUDE.md §4.4, JLS 15.26.2 / 15.14.2 / 15.15.1) ---------------------

  /** Does this lvalue contain a subexpression whose re-evaluation could have an effect?
    * `effectFree` conservatively returns `false` for every `ArrayAccess`, but `arr(0)` with both
    * effect-free needs no binding. Looks ONE LEVEL inside an assignable form for a non-trivial
    * constituent — the question the compound-assignment and increment arms need. */
  private[emit] def hasNonTrivialSubexpr(lv: Term): Boolean = lv match
    case _: Tree.Ident | _: Tree.This | _: Tree.Literal => false
    case Tree.Select(q, _, _, _)                        => !effectFree(q)
    case Tree.ArrayAccess(arr, idx, _, _)               => !effectFree(arr) || !effectFree(idx)
    case _                                              => true

  /** Bind the non-trivial subexpressions of an assignable lvalue to temporaries, returning
    * (list-of-val-bindings, bound-lvalue-string).
    *
    * For `arr(f())`: `(List("val $lv1 = f()"), "arr($lv1)")`
    * For `g().field`: `(List("val $lv1 = g()"), "$lv1.field")` */
  private[emit] def bindLvalue(lv: Term, i: Int): (List[String], String) = lv match
    case Tree.ArrayAccess(arr, idx, _, _) =>
      val bindings = List.newBuilder[String]
      val arrStr =
        if effectFree(arr) then term(arr, i)
        else { lvSeq += 1; val n = s"$$lv$lvSeq"; bindings += s"val $n = ${term(arr, i)}"; n }
      val idxStr =
        if effectFree(idx) then term(idx, i)
        else { lvSeq += 1; val n = s"$$lv$lvSeq"; bindings += s"val $n = ${term(idx, i)}"; n }
      (bindings.result(), s"$arrStr($idxStr)")
    case Tree.Select(qual, fld, _, _) =>
      lvSeq += 1
      val n = s"$$lv$lvSeq"
      (List(s"val $n = ${term(qual, i)}"), s"$n.${local(fld)}")
    case _ =>
      // fallback: bind the whole thing
      lvSeq += 1
      val n = s"$$lv$lvSeq"
      (List(s"val $n = ${term(lv, i)}"), n)

  // `compoundAssignParts` removed — the compound-assignment fact is now carried on `Tree.Assign`'s
  // `compound` field, set by the frontend. No shape reconstruction needed.

  /** A `Tree.Repeated` in an ARGUMENT position is the argument list's TAIL, not one argument —
    * decisive at ZERO elements, where a node rendering `""` would leave `f(a, )` instead of `f(a)`
    * (java's f(a) against f(A, B...), e.g. Paths.get(".")). Flattened HERE, a fact about the
    * position: the same node elsewhere still stands for a sequence of its own. */
  private[emit] def argTerms(args: List[Term]): List[Term] =
    if !args.exists(_.isInstanceOf[Tree.Repeated]) then args
    else args.flatMap { case Tree.Repeated(es, _, _) => es; case a => List(a) }

  private[emit] def applyStr(fun: Term, argsIn: List[Term], i: Int): String =
    applyStr0(fun, argTerms(argsIn), i)

  private[emit] def applyStr0(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them scala.<op>#…) render infix/prefix, not .op(x) — EXCEPT on a
    // super receiver, where scala's grammar admits super only as a selection qualifier (`super
    // ++= m` is a syntax error; `super.++=(m)` is the only legal spelling).
    case Tree.Select(recv: Tree.Super, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"${operand(recv, i)}.${esc(sym(m).name)}(${args.map(term(_, i)).mkString(", ")})"
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then prefixOp(op.stripPrefix("unary_"), operand(recv, i))
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    // JAVA PERMITS A STATIC MEMBER CALLED THROUGH AN INSTANCE (`family.one(…)`, `one` static);
    // scala's static emits into the companion, unreachable from an instance. Java evaluates and
    // DISCARDS the receiver, so an effectful receiver is evaluated first in a block (§4.4) and an
    // effect-free one is simply dropped.
    case Tree.Select(recv, m, _, _) if staticThroughInstance(recv, m) =>
      val call = s"${typeValue(sym(m).owner)}.${local(m)}(${args.map(term(_, i)).mkString(", ")})"
      if effectFree(recv) then call else s"{ ${term(recv, i)}; $call }"
    case Tree.Select(recv, m, _, _) if numericOverloadAscription(recv, m).isDefined =>
      s"(${term(fun, i)}: ${numericOverloadAscription(recv, m).get})(${args.map(term(_, i)).mkString(", ")})"
    // `X.values()` on an enum this emitter renders as a scala 3 enum: the desugaring's values is
    // PARENLESS, so the parens come off here. Asked of the ENUM'S OWN DECLARATION (EnumShape), not
    // the callee symbol — the frontend interns an enum's synthesised values under an anonymous
    // owner (§4.59), but the QUALIFIER's class symbol is exact.
    case Tree.Select(qual, m, _, _) if args.isEmpty && sym(m).name == "values" && scalaEnumQualifier(qual) =>
      term(fun, i)
    // T22 — a.name() on an ANNOTATION THIS PROGRAM DECLARES: java's element is both the write-name
    // and the read-accessor, but the emitted class keeps java's name at the constructor parameter
    // only, so the read is a field selection and the parens come off. Asked of the callee's OWNER
    // and PROGRAM OWNERSHIP (§4.56), never the name — an external annotation stays a method call.
    case Tree.Select(_, m, _, _) if args.isEmpty && emittedAnnotationElement(m) =>
      term(fun, i)
    // P11 — EXTERNAL PARENLESS: a member listed in `externalParenless` is called WITHOUT `()`.
    // Legal on the JVM too (Scala 3 auto-applies a Java nullary method), and required on JS/Native
    // where the platform shim declares the member parenless.
    case Tree.Select(_, m, _, _) if args.isEmpty && isExternalParenless(m) =>
      term(fun, i)
    case _ =>
      // through an ASCRIPTION, which wraps the callee without changing which member it is, so a
      // pinned resolutions selection does not disable the raw-parent alignment.
      def callee(t: Term): Option[SymId] = t match
        case Tree.Select(_, m, _, _)    => Some(m)
        case Tree.Ident(m, _, _)        => Some(m)
        case Tree.Typed(inner, _, _, _) => callee(inner)
        case _                          => scala.None
      val as = callee(fun).flatMap(alignedArgs(_, args, i)).getOrElse(args.map(term(_, i)))
      s"${term(fun, i)}(${as.mkString(", ")})"

  /** does this qualifier NAME a type this emitter renders as a scala 3 `enum`? A TYPE and never a
    * value: a `Select` is admitted beside an `Ident` for a NESTED enum's `Outer.Inner`, and the
    * answer comes from `EnumShape`, so a value of enum type (`l.values()`) cannot be mistaken —
    * its symbol is a local, not a class. */
  private[emit] def scalaEnumQualifier(qual: Term): Boolean =
    val s = qual match
      case Tree.Ident(s, _, _)     => s
      case Tree.Select(_, s, _, _) => s
      case _                       => SymId.None
    program.definitionOf(s).collect { case cd: Tree.ClassDef => cd }
      .exists(balticporter.tir.EnumShape.isScalaEnum(program, _))

  /** is this callee an ELEMENT of an `@interface` THIS PROGRAM DECLARES — a constructor parameter
    * `classDef1`'s annotation arm emitted? Three structural conjuncts, none a name (§4.56): owner
    * is program-OWNED, owner's flag says java wrote @interface, and callee takes no parameters
    * (JLS 9.6 admits only elements, constants and member types). */
  private[emit] def emittedAnnotationElement(m: SymId): Boolean =
    val o = sym(m).owner
    o != SymId.None && program.owns(o) && sym(o).flags.isAnnotation &&
      (sym(m).info match
        case TypeRepr.MethodType(ps, _, _) => ps.isEmpty
        case _                             => false)

  /** widening rank — a value of rank r converts implicitly to any numeric type of higher rank.
    * `Char` and `Short` share a rank because neither widens to the other. */
  private[emit] val numericRank = Map("scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2,
                                "scala.Int" -> 3, "scala.Long" -> 4, "scala.Float" -> 5,
                                "scala.Double" -> 6)

  /** Java resolves an overload by EXACT match; scala widens numerics first and finds no
    * most-specific alternative. Ascribing the method's function type names the alternative java
    * chose. Fires only where a same-name/arity sibling is WEAKLY WIDER everywhere and strictly
    * wider at one position (175 sites measured, 1 ambiguous). RESULT goes through [[ParentSubst]]
    * (G12); an unreachable substitution DECLINES the ascription (T17's stated refusal). */
  private[emit] def numericOverloadAscription(recv: Term, m: SymId): Option[String] =
    def numericParams(d: Tree.DefDef): Option[List[TypeRepr]] =
      val ps = d.paramss.flatten.map(_.tpt.tpe)
      Option.when(ps.nonEmpty && ps.forall(p => headSymOf(p).exists(s => numericRank.contains(sym(s).fullName))))(ps)
    def rank(t: TypeRepr): Int = headSymOf(t).flatMap(s => numericRank.get(sym(s).fullName)).getOrElse(0)
    def absorbs(wider: List[TypeRepr], here: List[TypeRepr]): Boolean =
      wider.sizeIs == here.size &&
        wider.zip(here).forall((w, h) => w == h || rank(w) > rank(h)) &&
        wider.zip(here).exists((w, h) => w != h)
    for
      d      <- program.definitionOf(m).collect { case d: Tree.DefDef => d }
      ps     <- numericParams(d)
      owner  <- program.definitionOf(sym(m).owner).collect { case c: Tree.ClassDef => c }
      if owner.body.exists {
        case o: Tree.DefDef =>
          o.symbol != m && sym(o.symbol).name == sym(m).name &&
            numericParams(o).exists(absorbs(_, ps))
        case _ => false
      }
      res     = ParentSubst.subst(d.returnTpt.tpe, receiverSubst(recv))
      // a RAW receiver binds the variable to a wildcard, which names nothing either — declined the
      // same way `OverloadRiskCheck.ascription` does (its bareWildcard is top-level only: List[?]
      // is a nameable result).
      if !res.isInstanceOf[TypeRepr.TypeBounds] && !namesForeignTypeParam(res)
    yield s"(${ps.map(tpe).mkString(", ")}) => ${tpe(res)}"

  /** what the RECEIVER's static type says an ancestor's type parameters are — [[ParentSubst.of]]
    * composed with the receiver's OWN application, collapsing `Bar.T` to `Int` for `Foo[Int] <:
    * Bar[X]` in one map. Empty for a receiver this program does not declare, or a raw one — the
    * caller's own nameability test then declines. */
  private[emit] def receiverSubst(recv: Term): Map[SymId, TypeRepr] =
    def headArgs(t: TypeRepr): Option[(SymId, List[TypeRepr])] = t match
      case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), as) => Some(s -> as)
      case TypeRepr.TypeRef(_, s)                           => Some(s -> Nil)
      case TypeRepr.ThisType(s)                             => Some(s -> Nil)
      case _                                                => scala.None
    headArgs(recv.tpe).flatMap { (s, as) =>
      program.definitionOf(s).collect { case c: Tree.ClassDef => c }.map { cd =>
        val own = cd.tparams.map(_.symbol).zip(as).toMap
        ParentSubst.of(cd)(using program).view.mapValues(ParentSubst.subst(_, own)).toMap ++ own
      }
    }.getOrElse(Map.empty)

  /** does this type mention a type PARAMETER no enclosing declaration here binds? Structural
    * (§4.56): a parameter's symbol is OWNED by the declaration that wrote it, so the question is
    * whether that owner is one of the classes this emitter is currently inside. */
  private[emit] def namesForeignTypeParam(t: TypeRepr): Boolean =
    def foreign(s: SymId): Boolean =
      val sm = sym(s)
      sm.flags.isParam && !classStack.contains(sm.owner)
    def go(x: TypeRepr): Boolean = x match
      case TypeRepr.TypeRef(_, s)        => foreign(s)
      case TypeRepr.AppliedType(tc, as)  => go(tc) || as.exists(go)
      case TypeRepr.AndType(l, r)        => go(l) || go(r)
      case TypeRepr.OrType(l, r)         => go(l) || go(r)
      case TypeRepr.ByNameType(u)        => go(u)
      case TypeRepr.TypeBounds(lo, hi)   => go(lo) || go(hi)
      case TypeRepr.Refinement(p, _, in) => go(p) || go(in)
      case TypeRepr.MethodType(pss, r, _) => pss.exists((_, pt) => go(pt)) || go(r)
      case _                             => false
    go(t)

  /** A Java anonymous class's body → Scala's anonymous-class expression `new Base(args) { … }`.
    * The symbol is pushed on `classStack` while members render, so `thisRef` qualifies an
    * enclosing reference as `Outer.this.m`. Captured locals need no lowering — scala closes over
    * them where javac synthesised ctor parameters. `Some(Nil)` still renders braces: `new Base()`
    * and `new Base(){}` are DIFFERENT types. */
  private[emit] def anonBody(anon: Option[Tree.AnonClass], i: Int): String = anon match
    case None    => ""
    case Some(a) =>
      classStack.append(a.symbol)
      val members = try a.body.map(stat(_, i + 1)).filter(_.trim.nonEmpty) finally classStack.removeLast()
      if members.isEmpty then " {}" else s" {\n${joinStats(members)}\n${ind(i)}}"

  /** parenthesize a term when it is an operand, where bare juxtaposition would misparse: an
    * operator application (precedence) and any control-flow expression (`if`/`match`, which scala
    * reads as "end of statement" otherwise). A RECEIVER IS AN OPERAND TOO — `(c ? a :
    * b).toString()` is ordinary java, and unparenthesised the method call binds to one branch only
    * (§4.4). Covers Select's qualifier, InstanceOf's, ArrayLength's and ArrayAccess's. */
  private[emit] def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => s"(${term(t, i)})"
    case _ => term(t, i)

  /** the same question for a term spliced into a [[Tree.Opaque]] HOLE, which is harder: a hole's
    * neighbours are whatever a policy entry wrote around `{recv}`, so this over-approximates by
    * three more node kinds than [[operand]] — a redundant parens pair costs two characters.
    * NOT folded into `operand`, which is on every emitted expression's path in every port. */
  private[emit] def spliceOperand(t: Term, i: Int): String = Tree.uncomment(t) match
    case _: Tree.Typed | _: Tree.Assign | _: Tree.InstanceOf => s"(${term(t, i)})"
    case _                                                   => operand(t, i)

  private[emit] def block(stats: List[Statement], expr: Term, trailing: List[Trivia], i: Int): String =
    // drop a redundant trailing `()` when the block already has statements (Java void bodies).
    val tail = expr match
      case Tree.Literal(Constant.UnitC, _, _) if stats.nonEmpty => Nil
      case _                                                    => List(ind(i + 1) + term(expr, i + 1))
    val lines = (stats.map(stat(_, i + 1)) ++ tail).filter(_.trim.nonEmpty) ++
                trailing.map(triviaText(_, i + 1))
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** join block statements, terminating one with `;` when the NEXT begins with `{` — otherwise
    * Scala greedily reads `new T(a)\n{ … }` as an anonymous-class body rather than two statements. */
  private[emit] def joinStats(lines: List[String]): String = lines match
    case Nil => ""
    case h :: t =>
      val sb = new StringBuilder(h)
      t.foreach { l => if firstCode(l).contains('{') then sb.append(";"); sb.append("\n").append(l) }
      sb.toString

  /** The first character a PARSER would see in `s` — comments skipped, exactly as scala's scanner
    * skips them. A bare `l.trim.startsWith("{")` test misses a preceding comment, which is
    * whitespace to the parser (measured: 2 "anonymous class cannot extend final class" errors). */
  private[emit] def firstCode(s: String): Option[Char] =
    var i     = 0
    var depth = 0
    while i < s.length do
      if depth > 0 then
        if s.startsWith("*/", i) then { depth -= 1; i += 2 }
        else if s.startsWith("/*", i) then { depth += 1; i += 2 }
        else i += 1
      else if s.startsWith("//", i) then
        val nl = s.indexOf('\n', i)
        i = if nl < 0 then s.length else nl + 1
      else if s.startsWith("/*", i) then { depth += 1; i += 2 }
      else if s.charAt(i).isWhitespace then i += 1
      else return Some(s.charAt(i))
    scala.None

  /** A java `try`, plus the arm that keeps a translated JUMP out of its handlers. Java's
    * break/continue is not an exception; scala's translation IS one (`boundary.Break extends
    * RuntimeException`), so a broad catch would silently swallow it — always the exception form
    * (dotty's `DropBreaks.prepareForTry`). Repair: a re-throw arm ahead of the java arms, only
    * where a jump really CROSSES the catch (`crossesCatch`). `finally` is untouched. */
  private[emit] def tryStr(t: Tree.Try, i: Int)(using Obligations): String =
    val (res, body, catches, fin) = (t.resources, t.body, t.catches, t.finalizer)
    // JS-S13 — try-with-resources closes on ANY completion, in reverse order, BEFORE this try's
    // own catch (ENGINE-LIMITS F5).
    Obligations.consult(JS.S(13), t.origin)(Option.when(res.nonEmpty)(()))
    // JS-S12 — a finally completing abruptly DISCARDS the try's own abrupt completion. Row stays
    // Partial: no corpus fixture has a finally that is itself the source (§2.3(a)).
    Obligations.consult(JS.S(12), t.origin)(Option.when(fin.isDefined)(()))
    val guard =
      if catches.exists(c => Jumps.catchesBreak(c.param.tpt.tpe)(using program)) && crossesCatch(body) then
        breakGuarded += t.id
        s"${ind(i + 1)}case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard}" +
          s" // §4.4: a java jump is not catchable\n"
      else ""
    // JS-S11 — a translated CATCH swallows a translated JUMP; read off the guard just decided so
    // the consult cannot drift from the decision.
    Obligations.consult(JS.S(11), t.origin)(Option.when(guard.nonEmpty)(()))
    // A MULTI-CATCH's union type must be PARENTHESISED in a typed pattern: bare `case e: A | B =>`
    // parses `|` as a PATTERN ALTERNATIVE, which may not bind a variable. Narrowed to the union.
    def catchTpe(t: TypeRepr): String = t match
      case _: TypeRepr.OrType => s"(${tpe(t)})"
      case _                  => tpe(t)
    val cs = catches.map { c =>
      // an unused catch variable is emitted as `_` — java commonly declares one it never reads
      // (`catch (Exception ignored)`), and `-Wunused:patvars` flags the name under `-Werror`.
      // The test: does any Ident in the body reference this symbol?
      val paramUsed = StandardTraversal.scanTerm(c.body, false) {
        case (true, _) => true
        case (_, Tree.Ident(s, _, _)) if s == c.param.symbol => true
        case (acc, _) => acc
      }(using program)
      val pname = if paramUsed then esc(sym(c.param.symbol).name) else "_"
      s"${ind(i + 1)}case $pname: ${catchTpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}"
    }.mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$guard$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    // The RESOURCES wrap the BODY and nothing else — JLS 14.20.3.2 defines an extended
    // try-with-resources as the basic one nested inside `try … Catches Finally`, i.e. every
    // resource is closed BEFORE this try's own `catch`/`finally` runs.
    if res.isEmpty then s"try ${term(body, i)}$cl$fl"
    else
      resourceLowered += t.id
      s"try ${resourceStr(res, body, i)}$cl$fl"

  /** JLS 14.20.3.1's lowering of a try-with-resources, emitted INLINE — not `Using`/a lambda
    * (this emitter's `return`/`break`/`continue` bind to labels outside the try). Reproduces
    * java's contract: reverse declaration order, every `close()` attempted after an earlier
    * throws, suppression on the body's own exception, closed on ANY completion including a jump
    * (a jump takes the `Break` arm ahead of the recorder). Numbered per nesting level. */
  private[emit] def resourceStr(res: List[Tree.ValDef], body: Term, i: Int)(using Obligations): String =
    res match
      case Nil => term(body, i)
      case v :: rest =>
        resourceSeq += 1
        val n    = resourceSeq
        val name = esc(sym(v.symbol).name)
        val p    = s"primary$$$n"
        val thr  = s"thrown$$$n"
        val sup  = s"suppressed$$$n"
        val inner = resourceStr(rest, body, i + 1)
        val b  = new StringBuilder
        b ++= "{\n"
        b ++= s"${ind(i + 1)}${valDef(v, 0)}\n"
        b ++= s"${ind(i + 1)}var $p: java.lang.Throwable = null\n"
        b ++= s"${ind(i + 1)}try $inner\n"
        // the JUMP arm, AHEAD of the recorder — see the doc above.
        b ++= s"${ind(i + 1)}catch { case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard} // §4.4: a java jump carries no exception to suppress into\n"
        b ++= s"${ind(i + 2)}case $thr: java.lang.Throwable => { $p = $thr; throw $thr } }\n"
        b ++= s"${ind(i + 1)}finally if $name != null then {\n"
        b ++= s"${ind(i + 2)}if $p != null then { try $name.close() catch { case $sup: java.lang.Throwable => $p.addSuppressed($sup) } }\n"
        b ++= s"${ind(i + 2)}else $name.close()\n"
        b ++= s"${ind(i + 1)}}\n"
        b ++= s"${ind(i)}}"
        b.toString

  /** one counter for every resource block this emitter opens — see [[resourceStr]] for why the
    * primary/thrown/suppressed binders may not repeat across a nesting. */
  private[emit] var resourceSeq = 0

  /** every `try` whose RESOURCES this emitter lowered — input to `try-resource`, which finds the
    * resource-carrying trys independently and reports the ones nothing closed. Keyed by
    * [[Tree.Try.id]]: an Origin is not unique across trys, nor is object identity after a rebuild. */
  private[emit] val resourceLowered = collection.mutable.Set.empty[TryId]
  def resourceLowerings: Tree.Try => Boolean = t => resourceLowered.contains(t.id)
  def resourceLoweringCount: Int = resourceLowered.size

  /** does a jump in this try BODY leave the try — i.e. is its `boundary` outside it? Read off the
    * emitter's own boundary state: a jump rendering as `boundary.break` has its target in scope
    * HERE, opened by a construct enclosing this try. A label bound INSIDE the body is not in these
    * maps yet, so the labelled lanes need no extra test to exclude it. */
  private[emit] def crossesCatch(body: Term): Boolean =
    (breakTarget.isDefined && Jumps.breaksOut(body)) ||
      (contTarget.isDefined && Jumps.continuesIn(body)) ||
      labelBreak.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = true)) ||
      labelCont.keysIterator.exists(l => Jumps.jumpsTo(body, l, brk = false))

  /** every `try` this emitter put a [[TirEmitter.BreakGuard]] arm on — input to `break-catch`,
    * which finds the crossings independently and reports the ones nothing guarded. Keyed by the
    * try's own TOKEN, never by Origin (two trys can share path/line/column, e.g. every
    * phase-synthesised one) and never by object identity (StandardTraversal rebuilds every node;
    * Tree.Try.id survives a rebuild because copy carries it). */
  private[emit] val breakGuarded = collection.mutable.Set.empty[TryId]
  def breakGuards: Tree.Try => Boolean = t => breakGuarded.contains(t.id)
  /** how many trys that is. */
  def breakGuardCount: Int = breakGuarded.size

  /** A java `switch`, with a boundary around any case body that still contains an unlabelled
    * `break` (mid-case fallthrough — the frontend strips only the CASE-TERMINATING one). Also
    * emits `case null => throw` ahead of the java arms for a REFERENCE selector java NPEs on
    * implicitly (JLS 14.11.2), unless the switch already declares its own `case null` (SE21). */
  private[emit] def matchStr(m: Tree.Match, i: Int)(using Obligations): String =
    val (scr, cases) = (m.scrutinee, m.cases)
    // JS-S06 — an unlabelled break in the MIDDLE of a case ends the CASE, and a match arm cannot
    // be left early. Fires where an arm really needs the boundary.
    Obligations.consult(JS.S(6), m.origin)(Option.when(cases.exists(c => caseNeedsBoundary(c.body)))(()))
    // JS-S08 — java throws NPE on a null reference selector IMPLICITLY (JLS 14.11.2); read off
    // selectorCanBeNull, the emitter's own decision (§4.56).
    Obligations.consult(JS.S(8), m.origin)(Option.when(selectorCanBeNull(scr, cases))(()))
    val cs = cases.map { c =>
      val bare = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      val pat = bare + c.guard.fold("")(g => s" if ${term(g, i)}")
      // a switch EXPRESSION's arm with a non-tail yield gets a VALUE-carrying boundary, mutually
      // exclusive with a mid-case break by java's own rules (JLS 15.28/14.21) — the Label's type
      // (Unit vs the expression's own) is what makes them two arms. caseYieldsOut descends through
      // a nested switch STATEMENT, whose own yield belongs to an arm further out.
      if m.isExpr && caseYieldsOut(c.body) then
        labelSeq += 1
        val n = s"yield$$$labelSeq"
        val b = inYield(Some(n))(term(c.body, i + 1))
        s"${ind(i + 1)}case $pat => scala.util.boundary { ($n: scala.util.boundary.Label[${tpe(m.tpe)}]) ?=> $b }"
      else if !caseNeedsBoundary(c.body) then s"${ind(i + 1)}case $pat => ${inSwitch(scala.None)(term(c.body, i + 1))}"
      else
        labelSeq += 1
        val n = s"case$$$labelSeq"
        val b = inSwitch(Some(n))(term(c.body, i + 1))
        s"${ind(i + 1)}case $pat => scala.util.boundary { ($n: scala.util.boundary.Label[scala.Unit]) ?=> $b }"
    }.mkString("\n")
    // the SCRUTINEE is outside the switch — a `break` cannot occur in a java expression — but it
    // is rendered AFTER the arms so that the boundary numbering does not move for a switch that
    // needed no change.
    val sel  = inSwitch(scala.None)(term(scr, i))
    val npe =
      if !selectorCanBeNull(scr, cases) then ""
      else
        nullGuardedSwitches += m.id
        s"${ind(i + 1)}case null => throw new java.lang.NullPointerException(" +
          "\"switch selector was null\") // §4.4: java's switch NPEs on a null reference selector\n"
    s"$sel match {\n$npe$cs\n${ind(i)}}"

  /** does java's implicit null check apply to this switch, and has nothing already written one?
    * Both needed: the selector's type is a REFERENCE type (a primitive cannot be null; decided
    * from the emitted type's head against scala's value classes), and no case label is already
    * `null` (SE21's pattern switch may deliberately handle it, JLS 14.11.1). */
  private[emit] def selectorCanBeNull(scr: Term, cases: List[Tree.CaseDef]): Boolean =
    val isValueClass = headSymOf(scr.tpe).map(s => sym(s).fullName).exists(TirEmitter.ScalaValueClasses.contains)
    val writesNull = cases.exists(_.labels.exists {
      case Tree.Literal(Constant.NullC, _, _) => true
      case _                                  => false
    })
    !isValueClass && !writesNull

  /** every switch this emitter gave a `case null` arm — input to `switch-null`. Keyed by
    * [[Tree.Match.id]], the reason `breakGuarded` is keyed by `Tree.Try.id`. */
  private[emit] val nullGuardedSwitches = collection.mutable.Set.empty[MatchId]
  def switchNullGuards: Tree.Match => Boolean = m => nullGuardedSwitches.contains(m.id)
  def switchNullGuardCount: Int = nullGuardedSwitches.size

  // ---- types ----
  /** a type in `new` position: `new Foo[?]` is illegal (you can't instantiate a wildcard), so
    * when a raw generic type carries wildcard args, drop them and let Scala infer the arguments
    * from the expected type (`new Foo(...)`). */
  /** As in `parentTpe`, only the HEAD is a `namedInner` position — the arguments are ordinary. */
  private[emit] def ctorTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, args) if args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) => byName(tpe(tc))
    case TypeRepr.AppliedType(tc, args) => s"${byName(tpe(tc))}[${args.map(tpe).mkString(", ")}]"
    case _ => byName(tpe(t))

  /** strip `scala.Array[...]` layers to the base element type (for `Array.ofDim[base](dims)`). */
  private[emit] def baseElem(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(e)) if sym(s).fullName == "scala.Array" => baseElem(e)
    case _ => t

  /** THE TYPE DISPATCH — the emitter half of the catalog's FOURTH obligation surface. At the
    * dispatch and never in an arm ([[stat]]'s reason): a TypeRepr is not a Tree, so [[Rendering]]
    * could never enter one. The subject is the TypeRepr; the origin is the enclosing scope's
    * (`CatalogLog.currentOrigin`), since a type has no position of its own. */
  private[emit] def tpe(t: TypeRepr): String =
    Typing.ofRepr(TirKinds.ofType(t), t)(tpeArm(t))

  /** JS-C29 and JS-G12 — the two questions a NAME is asked at the type dispatch's TypeRef arm,
    * read off the SYMBOL rather than by re-running typeSym's cascade. */
  private[emit] def typeRefConsults(s: SymId)(using Obligations): Unit =
    val full    = sym(s).fullName
    val marker  = Symbol.isUnresolvedTypeVar(full)
    // JS-C29 — a java NESTED type is one of two different scala types, only one path-dependent.
    // Every $ in a full name is that question; a marker is excluded (it is the other row's).
    Obligations.consult(JS.C(29), catalog.currentOrigin)(Option.when(!marker && full.contains('$'))(()))
    // JS-G12 — the emitter's half: an unresolved type variable is a MARKER, so ? is emitted
    // (ENGINE-LIMITS G2 — one occurrence took out the statement around it).
    Obligations.consult(JS.G(12), catalog.currentOrigin)(Option.when(marker)(()))

  /** JS-G01's EMITTER half — the bound GRAMMAR, stated once and called from BOTH TypeBounds arms
    * (the bare-wildcard fast path would otherwise be a hole at every plain `?`, ENGINE-LIMITS F8). */
  private[emit] def boundsConsults(lo: TypeRepr, hi: TypeRepr)(using Obligations): Unit =
    def written(b: TypeRepr) = b != TypeRepr.NoType && !isUnresolvedTypeVar(b)
    Obligations.consult(JS.G(1), catalog.currentOrigin)(Option.when(written(lo) || written(hi))(()))

  private[emit] def tpeArm(t: TypeRepr)(using Obligations): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeRefConsults(s); typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) =>
      boundsConsults(TypeRepr.NoType, TypeRepr.NoType); "?"
    // a BOUND that is an unresolved type variable says nothing, and saying it is worse than
    // silence, so it is dropped, leaving a bare ? (G2).
    case TypeRepr.TypeBounds(lo, hi) =>
      boundsConsults(lo, hi)
      val l = if lo == TypeRepr.NoType || isUnresolvedTypeVar(lo) then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType || isUnresolvedTypeVar(hi) then "" else s" <: ${tpe(hi)}"
      s"?$l$h"
    case TypeRepr.Refinement(p, _, _)          => tpe(p)
    case TypeRepr.MethodType(ps, res, _)       => s"(${ps.map((_, pt) => tpe(pt)).mkString(", ")}) => ${tpe(res)}"
    case TypeRepr.PolyType(_, res)             => tpe(res)
    case TypeRepr.TypeLambda(ps, body)         => s"[${ps.map(_._1).mkString(", ")}] =>> ${tpe(body)}"
    case TypeRepr.ParamRef(_, _)               => "?"

  // ---- constants ----
  private[emit] def constant(c: Constant): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => v.toString
    case Constant.ShortC(v)  => v.toString
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => v.toString
    case Constant.CharC('\'') => "'\\''" // a single-quote char must be escaped inside `'…'`
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.StringC(v) => "\"" + escape(v) + "\""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t)}]"

  /** JS-E20 — a PRIMITIVE class literal. `int.class` is statically `Class<Integer>` (JLS 15.8.2)
    * while scala's `classOf[Int]` is `Class[Int]`, which no `Class[T <: Object]` slot accepts.
    * Both facts kept: the runtime object stays the primitive class, the STATIC type becomes
    * java's. `None` at every other constant, which is where the consult does not apply. */
  private[emit] def primitiveClassLiteral(c: Constant): Option[String] = c match
    case Constant.ClassOfC(t @ TypeRepr.TypeRef(_, s)) =>
      Descriptor.ValueClassBoxes.get(sym(s).fullName)
        .map(b => s"classOf[${tpe(t)}].asInstanceOf[java.lang.Class[$b]]")
    case _ => scala.None

  /** A PREFIX operator and its operand, with the two kept as two tokens. Scala's lexer takes a
    * maximal run of operator characters as ONE identifier, so a prefix `-` against an operand
    * already rendering with a leading `-` produces `--`, a different token (e.g. java negating a
    * literal whose value is already negative — measured 48 errors in one method). Parenthesising
    * the operand is the only fix that cannot mis-lex; a separating space would misparse as infix. */
  private[emit] def prefixOp(op: String, rendered: String): String =
    if op.nonEmpty && rendered.nonEmpty && isOpChar(op.last) && isOpChar(rendered.head)
    then s"$op($rendered)"
    else s"$op$rendered"

  /** the ASCII half of Scala's `opchar` (SLS 1.1); the Unicode `Sm`/`So` half cannot begin any
    * rendering this emitter produces. */
  private[emit] def isOpChar(c: Char): Boolean = "!#%&*+-/:<=>?@\\^|~".indexOf(c.toInt) >= 0

  /** Render a string or char literal's VALUE as Scala source that denotes the same value. Every
    * character needs escaping that would otherwise: end the literal (raw `\n`), be an "illegal
    * character" (a raw control char), or silently change on UTF-8 write-out (a lone surrogate).
    * Everything else, including ordinary non-ASCII text, is emitted verbatim. `\uXXXX` is a scala 3
    * escape sequence expanded inside the literal only, so an emitted `\\u` cannot leak into one. */
  private[emit] def escape(s: String): String =
    val b = new StringBuilder(s.length)
    s.foreach { c =>
      c match
        case '\\'   => b ++= "\\\\"
        case '"'    => b ++= "\\\""
        case '\b'   => b ++= "\\b"
        case '\t'   => b ++= "\\t"
        case '\n'   => b ++= "\\n"
        case '\f'   => b ++= "\\f"
        case '\r'   => b ++= "\\r"
        case _ if c < ' ' || c.toInt == 0x7f || Character.isSurrogate(c) =>
          b ++= "\\u"; b ++= f"${c.toInt}%04x"
        case _      => b += c
    }
    b.result()

/** THE `Tree` KIND, as the rendering dispatch names it. `Tree` is sealed and every case is a case
  * class, so the name is the compiler's own `productPrefix` — a function, not a table, since a new
  * node kind needs no listing to be covered. */
private object TirKinds:
  def of(t: Tree): String = t match
    case p: Product => p.productPrefix
    // unreachable: every concrete Tree is a case class.
    case _          => "?"

  /** the TYPE algebra's, for the fourth obligation surface — same derivation, same reason. */
  def ofType(t: TypeRepr): String = t match
    case p: Product => p.productPrefix
    case _          => "?"
