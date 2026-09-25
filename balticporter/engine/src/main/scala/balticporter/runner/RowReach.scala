package balticporter.runner

import balticporter.runner.RowSurface.Access
import balticporter.tir.{ OverrideGraph, Program, SymId, Symbol, Tree, TypeRepr, Usage, Xref }

/** What one translation's TIR says about the declarations of a row's shadowed files, keyed like [[RowSurface]] with `#` read as `.`: the access JAVA declared (enclosing types included), the arities
  * of the constructors java declared, and every site OUTSIDE the shadowed files that references, extends or overrides one of them. The surface check compares only what shared code can reach, so these
  * are resolved references, never text.
  */
object RowReach:

  final case class View(access: Map[String, Access], ctorArities: Map[String, Set[Int]], outside: Map[String, List[String]])

  /** a [[RowSurface]] key as a view key: class and companion members are one java type's members. */
  def normal(key: String): String = key.replace('#', '.')

  /** @param shadowed
    *   the top-level units of the shadowed files; `outside` the units the shared code consists of; `site` renders one referencing position.
    */
  def of(p: Program, shadowed: List[Tree.ClassDef], outside: List[Tree.ClassDef], site: (SymId, balticporter.tir.Origin) => String): View =
    val roots = shadowed.map(_.symbol).toSet
    def sym(id:    SymId): Option[Symbol] = p.symbolOf(id)
    def isType(id: SymId): Boolean        = p.definitionOf(id).exists(_.isInstanceOf[Tree.ClassDef])
    // the chain up to (and including) a shadowed unit, or None when `id` is not declared in one
    def chain(id: SymId): Option[List[Symbol]] =
      def go(at: SymId, fuel: Int, acc: List[Symbol]): Option[List[Symbol]] =
        if at == SymId.None || fuel == 0 then scala.None
        else sym(at).flatMap(s => if roots(at) then Some((s :: acc).reverse) else go(s.owner, fuel - 1, s :: acc))
      go(id, 64, Nil)
    def own(s: Symbol): Access =
      if s.flags.isPrivate then Access.Private
      else if s.flags.isPackagePrivate then Access.PackagePrivate
      else if s.flags.isProtected then Access.Protected
      else Access.Public
    def flat(fullName: String): String = fullName.replace('$', '.').replace('#', '.')
    // a type's own name, or a member of a type: locals, parameters and members of a local class are nobody's surface
    def keyOf(s: Symbol, ch: List[Symbol]): Option[String] =
      if isType(s.id) then Option.when(ch.tail.forall(o => isType(o.id)))(flat(s.fullName))
      else
        ch.tail match
          case owner :: rest if isType(owner.id) && rest.forall(o => isType(o.id)) =>
            Some(flat(owner.fullName) + "." + (if s.name == "<init>" then "this" else s.name))
          case _ => scala.None

    val declared: List[(Symbol, String, Access)] = p.symbols.all.toList.flatMap { s =>
      chain(s.id).flatMap(ch => keyOf(s, ch).map(k => (s, k, ch.map(own).minBy(_.ordinal))))
    }
    val access = declared.groupMapReduce(_._2)(_._3)((a, b) => if a.ordinal >= b.ordinal then a else b)
    def arity(t: TypeRepr): Int = t match
      case TypeRepr.MethodType(ps, _, _) => ps.size
      case TypeRepr.PolyType(_, r)       => arity(r)
      case _                             => 0
    val ctorArities = declared.collect { case (s, k, _) if s.name == "<init>" => k.stripSuffix(".this") -> arity(s.info) }.groupMap(_._1)(_._2).view.mapValues(_.toSet).toMap

    val byId = declared.map((s, k, _) => s.id -> k).toMap
    val refs = Xref.build(outside)
    // a usage's enclosing definition may be a local; the site is named by the member holding it
    def member(id: SymId, fuel: Int = 64): SymId =
      sym(id) match
        case Some(s) if fuel > 0 && s.owner != SymId.None && !isType(s.owner) && !isType(id) => member(s.owner, fuel - 1)
        case _                                                                               => id
    val used  = byId.toList.flatMap((id, k) => refs.usages(id).map { case Usage(_, t, enc) => k -> site(member(enc), t.origin) })
    val og    = OverrideGraph.build(p)
    val mine  = byId.keySet
    val overs = byId.toList.flatMap { (id, k) =>
      og.overriders(id).filterNot(mine).flatMap(o => sym(o).map(os => k -> (site(o, os.origin) + " (overrides it)")))
    }
    View(access, ctorArities, (used ++ overs).groupMap(_._1)(_._2).view.mapValues(_.distinct.sorted).toMap)
