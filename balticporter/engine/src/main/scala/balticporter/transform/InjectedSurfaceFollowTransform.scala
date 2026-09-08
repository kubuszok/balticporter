package balticporter.transform

import balticporter.emit.InjectedSurface
import balticporter.tir.*

/** Calls into a DROPPED type follow the INJECTED file's spelling: a java getter/setter of a type
  * the port replaced (`Pixmap.getWidth()` against sge's `Pixmap` with `width`) is renamed on the
  * SYMBOL — never emitted as a declaration, rendered at every use — when the injected surface
  * lacks the java name at that arity but spells the property (`x`, `x_=`, `x(i)`). The emitter's
  * arity follow (`InjectedSurface.memberHasParens`) then drops the `()`. Derived, no key; §1(a):
  * a replacement's surface is a fact the port already stated by injecting it. `PROGRESS.md` §13.31. */
final class InjectedSurfaceFollowTransform(surface: InjectedSurface.Surface,
                                           /** dropped upstream FQN -> the emitted FQN the injected file declares */
                                           droppedEmitted: Map[String, String]) extends Phase:
  def name = "injected-surface-follow"
  override def runsAfter: Set[String]  = Set("bean-properties", "nullary-arity")
  override def runsBefore: Set[String] = Set("package-rename")

  private def decap(s: String) = if s.nonEmpty && s.head.isUpper then s.head.toLower + s.tail else s
  /** `GLType` -> `glType`: a leading acronym lowered whole (the other spelling, `gLType`, is decap's) */
  private def lowerAcronym(s: String) =
    val run = s.takeWhile(_.isUpper)
    // `GLType`: the run is `GLT`, and its LAST capital starts the next word
    if run.length > 1 && run.length < s.length then run.init.toLowerCase + s.drop(run.length - 1)
    else if run.length > 1 then run.toLowerCase else decap(s)
  /** the property spellings a java accessor may have in the hand port, most conventional first */
  private def propertyNames(n: String): List[String] =
    val stem =
      if n.length > 3 && n.startsWith("get") && n(3).isUpper then Some(n.drop(3))
      else if n.length > 2 && n.startsWith("is") && n(2).isUpper then Some(n.drop(2))
      else if n.length > 3 && n.startsWith("set") && n(3).isUpper then Some(n.drop(3))
      else None
    stem.toList.flatMap(x => List(decap(x), lowerAcronym(x)).distinct)

  override def run(program: Program): Program =
    if surface.isEmpty || droppedEmitted.isEmpty then return program
    /** the EMITTED owner of a member of a dropped type — the dropped top-level type's emitted name
      * plus the nesting below it (`Pixmap$Format` -> `sge.graphics.Pixmap.Format`); None outside */
    /** an interned external member (`@<owner>#name()`, the frontend's spelling for a member it
      * could not resolve scope-free — `Enum#name` per enum type) carries its owner in the NAME */
    def ownerOf(s: Symbol): SymId =
      if s.owner != SymId.None then s.owner
      else "^@(\\d+)#".r.findFirstMatchIn(s.fullName).map(m => SymId(m.group(1).toInt)).getOrElse(SymId.None)
    def emittedOwnerOf(s: Symbol): Option[String] =
      val chain = Iterator.iterate(ownerOf(s))(id => program.symbolOf(id).map(_.owner).getOrElse(SymId.None))
        .takeWhile(_ != SymId.None).take(8).flatMap(program.symbolOf).toList
      chain.indexWhere(o => droppedEmitted.contains(o.fullName)) match
        case -1 => None
        case i  => Some((droppedEmitted(chain(i).fullName) :: chain.take(i).reverse.map(_.name)).mkString("."))
    val renamed = program.symbols.all.toList.sortBy(_.id.raw).flatMap { s =>
      if s.flags.isParam || s.name == "<init>" || s.name.contains('$') then Nil
      else emittedOwnerOf(s).toList.flatMap { emittedOwner =>
        val n = arityOf(s)
        if surface.lookup(emittedOwner, s.name, n).isDefined then Nil
        else
          val candidates = propertyNames(s.name).flatMap { prop =>
            if s.name.startsWith("set") && n == 1 then List(prop + "_=", prop) else List(prop)
          }
          // a setter against the injected `var x`: the member is `x` at arity 0, the call is `x_=`
          candidates.find(c => surface.lookup(emittedOwner, c, n).isDefined || (n == 0 && surface.memberHasParens(emittedOwner, c).isDefined)
              || (n == 1 && c.endsWith("_=") && surface.memberHasParens(emittedOwner, c.stripSuffix("_=")).isDefined))
            .map(to => s -> to).toList
      }
    }
    if renamed.isEmpty then return program
    // the injected spelling's ARITY: a `def width: Pixels` has no parens, so the call loses its `()`
    parenless = renamed.collect { case (s, to) if arityOf(s) == 0 &&
      emittedOwnerOf(s).exists(o => surface.memberHasParens(o, to).contains(false)) => s.id }.toSet
    renamed.foreach { (s, to) =>
      record(Decision(
        kind = Decision.Kind.RenamedMember, subject = s.id, subjectFqn = s.fullName,
        detail = Map("from" -> s.name, "to" -> to,
          "why" -> "a call into a DROPPED type follows the injected replacement's spelling — the java accessor name is not on the injected surface, the property is"),
        reason = Reason.Universal("injected-surface-follow"),
        origin = Decision.originOf(program, s.id)))
    }
    val byId = renamed.map((s, to) => s.id -> to).toMap
    val p2 = program.rebuilt(symbols = SymbolTable(program.symbols.all.map(s => byId.get(s.id).fold(s)(to => s.copy(name = to)))))
    given Program = p2
    p2.rebuilt(units = p2.units.map(u => StandardTraversal.mapClassDef(this, u)))

  private var parenless: Set[SymId] = Set.empty
  private def arityOf(s: Symbol): Int = s.info match
    case TypeRepr.MethodType(ps, _, _)                  => ps.size
    case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.size
    case _                                              => 0

  /** `recv.getWidth()` -> `recv.width`: the call's parens go where the injected member has none;
    * `javaEnum.name()` on a dropped enum replaced by a scala 3 `enum` -> `toString` (Enum's
    * `name` is java.lang.Enum's, no symbol of the dropped type's own to rename). */
  override def transformApply(t: Tree.Apply)(using p: Program): Term = t match
    case Tree.Apply(Tree.Select(q, _, _, _), Nil, m, tpe, o) if parenless(m) => Tree.Select(q, m, tpe, o)
    // `javaEnum.name()` on a DROPPED enum replaced by a scala 3 `enum`: `java.lang.Enum#name` is ONE
    // interned symbol shared by every enum (the emitted ones extend `java.lang.Enum` and keep it),
    // so the call — not the symbol — becomes `.toString`, spliced over the receiver term
    case Tree.Apply(Tree.Select(q, _, _, _), Nil, m, tpe, o)
        if p.symbolOf(m).exists(ms => ms.name.stripSuffix("()") == "name" && arityOf(ms) == 0
              && p.symbolOf(ms.owner).exists(_.fullName == "java.lang.Enum")) && receiverDropped(q) =>
      Tree.Opaque.spliced(List("", ".toString"), List(q), tpe, o)
    // `new X(args)` on a dropped type whose injected COMPANION spells the construction as `apply`
    // (`ETC1.ETC1Data(pkmFile)`): the call goes to the factory — a constructor the injected class
    // does not have would not compile, and an `apply` beside one it does is the same call
    case Tree.Apply(fun, args, m, tpe, o)
        if p.symbolOf(m).exists(_.name == "<init>") && emittedOwnerOfType(tpe).exists(owner =>
             surface.lookup(owner, "apply", args.size).isDefined) =>
      val owner = emittedOwnerOfType(tpe).get
      val parts = (owner + "(") :: List.fill(math.max(args.size - 1, 0))(", ") ::: List(")")
      Tree.Opaque.spliced(if args.isEmpty then List(owner + "()") else parts, args, tpe, o)
    case other => other

  /** the emitted owner name of a constructed type that a dropped type declares (itself or nested). */
  private def emittedOwnerOfType(tpe: TypeRepr)(using p: Program): Option[String] =
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, sym)    => Some(sym)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => None
    head(tpe).flatMap(p.symbolOf).flatMap { cls =>
      val chain = (cls :: Iterator.iterate(cls.owner)(id => p.symbolOf(id).map(_.owner).getOrElse(SymId.None))
        .takeWhile(_ != SymId.None).take(8).flatMap(p.symbolOf).toList)
      chain.indexWhere(o => droppedEmitted.contains(o.fullName)) match
        case -1 => None
        case i  => Some((droppedEmitted(chain(i).fullName) :: chain.take(i).reverse.map(_.name)).mkString("."))
    }

  /** is the receiver's type (or an enclosing type of it) one the port dropped and injected? */
  private def receiverDropped(q: Term)(using p: Program): Boolean =
    def head(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, sym)    => Some(sym)
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case _                           => None
    head(q.tpe).exists { sym =>
      Iterator.iterate(sym)(id => p.symbolOf(id).map(_.owner).getOrElse(SymId.None))
        .takeWhile(_ != SymId.None).take(8).flatMap(p.symbolOf).exists(x => droppedEmitted.contains(x.fullName))
    }
