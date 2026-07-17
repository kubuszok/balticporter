package balticporter.emit

import balticporter.core.*
import balticporter.core.BExpr.*

/** How one field renders. Pure structure — the printer does all rendering. */
enum FieldLine:
  /** ordinary field declaration with an initializer (own or fused from the single ctor). */
  case FromField(f: BField, init: BExpr)
  /** null-sentinel merge: `val f: T = if (_p != null) _p else <default>`. */
  case SentinelVal(f: BField, paramName: String, default: BExpr)
  /** non-final field with no initializer anywhere → Java default value. */
  case DefaultInit(f: BField)

/** The primary/secondary constructor layout for a class (PLAN.md funnel strategies).
  *
  * Java constructor graphs don't map 1:1 onto Scala's primary/auxiliary model
  * (auxiliaries can never call `super`); this module picks one of three shapes:
  *
  *  1. zero/one constructor → it becomes the primary; a final field assigned
  *     exactly once as `this.f = f` from the same-named parameter becomes a
  *     `val` class parameter; other single-assigned final fields become
  *     `val f = expr` members; remaining body statements stay as class body;
  *  2. several constructors with empty bodies where exactly one calls `super`
  *     with exactly its own parameters → that one is primary, the others
  *     delegate (`def this(...) = this(<their super/this args>)`);
  *  3. exactly two constructors — `(p)` assigning a final ref-typed field from
  *     its parameter and `()` assigning the same field from a no-param
  *     expression — merge via the null-sentinel idiom used across the
  *     hand-ported corpus: primary `(_p: T)`, secondary `def this() = this(null)`,
  *     `val f: T = if (_p != null) _p else <expr>`.
  *
  * Anything else is Unsupported: the engine refuses rather than approximates.
  */
final case class CtorPlan(
    primaryParams: List[CtorPlan.Param],
    primaryMods: Option[Mods],
    /** the primary Java ctor's comments — hoisted above the class line, since the
      * primary constructor has no declaration of its own in Scala. */
    primaryLeading: List[Trivia],
    superArgs: List[BExpr],
    primaryBody: List[BStmt],
    fieldLines: List[FieldLine],
    secondaryCtors: List[CtorPlan.Secondary],
)

object CtorPlan:
  /** promoted=true renders as `<vis>val name: T`; the vis comes from the promoted field. */
  final case class Param(p: BParam, promoted: Option[BField])
  final case class Secondary(leading: List[Trivia], mods: Mods, params: List[BParam], delegateArgs: List[BExpr])

  def of(t: BTypeDecl, unit: BUnit): CtorPlan =
    def fail(what: String): Nothing = throw Unsupported(unit.sourcePath, t.name, what)

    def fieldWithOwnInit(f: BField): FieldLine =
      f.init match
        case Some(i)                 => FieldLine.FromField(f, i)
        case None if !f.mods.isFinal => FieldLine.DefaultInit(f)
        case None => fail(s"final field ${f.name} has no initializer and no constructor assigns it")

    /** `this.f = <e>` assignments in a ctor body, in order; everything else stays. */
    def splitAssigns(body: List[BStmt]): (List[(String, BExpr)], List[BStmt]) =
      val assigns = List.newBuilder[(String, BExpr)]
      val rest = List.newBuilder[BStmt]
      body.foreach { s =>
        s.k match
          case BStmtK.Assign(Ident(f, RefKind.OwnField), rhs, None) => assigns += (f -> rhs)
          case BStmtK.Empty                                         => ()
          case _                                                    => rest += s
      }
      (assigns.result(), rest.result())

    t.ctors match
      case Nil =>
        CtorPlan(Nil, None, Nil, Nil, Nil, t.fields.map(fieldWithOwnInit), Nil)

      case c :: Nil =>
        c.thisArgs.foreach(_ => fail("single constructor delegating to this(...)"))
        val (assigns, rest) = splitAssigns(c.body)
        val counts = assigns.groupBy(_._1).view.mapValues(_.length).toMap
        counts.find(_._2 > 1).foreach { case (f, _) => fail(s"field $f assigned more than once in ctor") }
        val assignedOnce = assigns.toMap
        val promoted: Map[String, BField] = assignedOnce.collect {
          case (f, Ident(p, RefKind.Param(_)))
              if p == f && t.fields.exists(fd => fd.name == f && fd.mods.isFinal) =>
            f -> t.fields.find(_.name == f).get
        }
        // non-final `this.f = f` can't promote (a member may not shadow a same-named class
        // param) — rename the param `_f` and initialize `var f = _f`
        val renamed: Set[String] = assignedOnce.collect {
          case (f, Ident(p, RefKind.Param(_)))
              if p == f && !promoted.contains(f) && t.fields.exists(_.name == f) =>
            f
        }.toSet
        val params = c.params.map { p =>
          if renamed.contains(p.name) then Param(p.copy(name = "_" + p.name), None)
          else Param(p, promoted.get(p.name))
        }
        val fieldLines = t.fields.flatMap { f =>
          if promoted.contains(f.name) then None
          else
            (f.init, assignedOnce.get(f.name)) match
              case (Some(i), None) => Some(FieldLine.FromField(f, i))
              case (None, Some(_)) if renamed.contains(f.name) =>
                Some(FieldLine.FromField(f, Ident("_" + f.name, RefKind.Param(false))))
              case (None, Some(e)) => Some(FieldLine.FromField(f, e))
              case (Some(_), Some(_)) => fail(s"field ${f.name} has an initializer and a ctor assignment")
              case (None, None) => Some(fieldWithOwnInit(f))
        }
        CtorPlan(params, Some(c.mods), c.leading, c.superArgs.getOrElse(Nil), rest, fieldLines, Nil)

      case ctors =>
        val allEmptyBodies = ctors.forall { c =>
          val (a, r) = splitAssigns(c.body); a.isEmpty && r.isEmpty
        }
        if allEmptyBodies then
          def isIdentitySuper(c: BCtor): Boolean = c.superArgs.exists { args =>
            args.length == c.params.length && args.zip(c.params).forall {
              case (Ident(n, RefKind.Param(_)), p) => n == p.name
              case _                               => false
            }
          }
          ctors.filter(isIdentitySuper) match
            case Nil => fail("multiple constructors, none with an identity super(...) call")
            case candidates =>
              // several identity-super ctors: the max-arity one is primary (first in
              // source order on ties — `candidates` preserves source order)
              val primary = candidates.maxBy(_.params.length)
              val secondaries = ctors.filterNot(_ eq primary).map { c =>
                val delegateArgs = c.thisArgs.orElse(c.superArgs).getOrElse(fail("secondary ctor without super/this args"))
                if delegateArgs.length != primary.params.length then
                  fail("secondary ctor cannot delegate to primary (arity mismatch)")
                Secondary(c.leading, c.mods, c.params, delegateArgs)
              }
              CtorPlan(
                primary.params.map(p => Param(p, None)),
                Some(primary.mods),
                primary.leading,
                primary.superArgs.getOrElse(Nil),
                Nil,
                t.fields.map(fieldWithOwnInit),
                secondaries,
              )
        else
          ctors.sortBy(_.params.length) match
            case List(noArg, paramful) if noArg.params.isEmpty && paramful.params.length == 1 =>
              val (na, nr) = splitAssigns(noArg.body)
              val (pa, pr) = splitAssigns(paramful.body)
              if nr.nonEmpty || pr.nonEmpty then fail("two-ctor merge: ctor bodies contain more than field assignments")
              (na, pa) match
                case (List((f1, defaultExpr)), List((f2, Ident(pn, RefKind.Param(_)))))
                    if f1 == f2 && paramful.params.head.name == pn &&
                      t.fields.exists(fd => fd.name == f1 && fd.mods.isFinal) =>
                  val p = paramful.params.head
                  if p.tpe.isInstanceOf[BType.Prim] then
                    fail("two-ctor merge: sentinel requires a reference-typed parameter")
                  val fld = t.fields.find(_.name == f1).get
                  CtorPlan(
                    List(Param(p.copy(name = "_" + p.name), None)),
                    Some(paramful.mods),
                    paramful.leading,
                    paramful.superArgs.getOrElse(Nil),
                    Nil,
                    FieldLine.SentinelVal(fld, "_" + p.name, defaultExpr)
                      :: t.fields.filterNot(_.name == f1).map(fieldWithOwnInit),
                    List(Secondary(noArg.leading, noArg.mods, Nil, List(Lit(LitKind.NullL, "null")))),
                  )
                case _ => fail("two-ctor merge: shapes don't match the sentinel pattern")
            case _ => fail(s"${ctors.length} constructors with field logic — no funnel strategy applies")
