package balticporter.frontend.ts

import scala.meta.*

/** Reads a hand-written reference Scala source STRUCTURALLY: every member a template, an extension group or a package declares, with the owner that declares it, its access and the line its name is
  * on. Indentation says nothing about ownership; the parse does.
  */
object ReferenceDeclarations:

  enum Kind:
    case Def, AbstractDef, Constructor, Val, Var, Type, Object, Class, Trait, Enum, EnumCase

  /** Where a method's body sits, 0-based: the signature's `=` (line and column) and the body's last line. */
  final case class BodySpan(equalsLine: Int, equalsColumn: Int, endLine: Int)

  /** `owner` is the path of the enclosing templates (`Outer.Inner`), empty at package level. `ownerIsObject` holds when every template on that path is an object, so the member is reachable as
    * `owner.name`. `isPrivate` is an UNQUALIFIED `private` or `protected`: such a member is not accessible from outside its owner. `line` is 0-based, the line of the member's NAME.
    */
  final case class Declaration(
    owner:         String,
    ownerIsObject: Boolean,
    name:          String,
    kind:          Kind,
    isPrivate:     Boolean,
    line:          Int,
    body:          Option[BodySpan] = None
  ):
    /** A member a reference skeleton emits as a method: a def, abstract or concrete, or a secondary constructor. */
    def isMethod: Boolean = kind == Kind.Def || kind == Kind.AbstractDef || kind == Kind.Constructor

  /** The declarations of one source, or the parser's message when it does not parse. */
  def read(fileName: String, source: String): Either[String, List[Declaration]] =
    dialects.Scala3(Input.VirtualFile(fileName, source)).parse[Source] match
      case e: Parsed.Error      => Left(s"$fileName: ${e.message}")
      case Parsed.Success(tree) =>
        val out = List.newBuilder[Declaration]

        def closed(mods: List[Mod]): Boolean =
          mods.exists {
            case p: Mod.Private   => p.within.syntax.isEmpty || p.within.is[Term.This]
            case p: Mod.Protected => p.within.syntax.isEmpty || p.within.is[Term.This]
            case _                => false
          }

        def child(owner: String, name: String): String = if owner.isEmpty then name else s"$owner.$name"

        def add(owner: String, isObj: Boolean, name: Name, kind: Kind, mods: List[Mod], body: Option[BodySpan] = None): Unit =
          out += Declaration(owner, isObj, name.value, kind, closed(mods), name.pos.startLine, body)

        // The signature's `=` is the last one before the body starts; a default parameter's comes earlier.
        def span(whole: Tree, body: Tree): Option[BodySpan] =
          whole.tokens.filter(t => t.is[Token.Equals] && t.end <= body.pos.start).lastOption.map { eq =>
            BodySpan(eq.pos.startLine, eq.pos.startColumn, body.pos.endLine)
          }

        def pats(ps: List[Pat]): List[Name] = ps.flatMap(_.collect { case Pat.Var(n) => n })

        def template(t: Template, owner: String, isObj: Boolean): Unit = t.body.stats.foreach(member(_, owner, isObj))

        def member(t: Tree, owner: String, isObj: Boolean): Unit = t match
          case p: Pkg                 => p.stats.foreach(member(_, "", true))
          case p: Pkg.Object          => template(p.templ, "", true)
          case d: Defn.Object         =>
            add(owner, isObj, d.name, Kind.Object, d.mods)
            template(d.templ, child(owner, d.name.value), isObj)
          case d: Defn.Class          =>
            add(owner, isObj, d.name, Kind.Class, d.mods)
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Trait          =>
            add(owner, isObj, d.name, Kind.Trait, d.mods)
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Enum           =>
            add(owner, isObj, d.name, Kind.Enum, d.mods)
            template(d.templ, child(owner, d.name.value), false)
          case d: Defn.Given          => template(d.templ, child(owner, d.name.value), false)
          case d: Defn.EnumCase       => add(owner, isObj, d.name, Kind.EnumCase, d.mods)
          case d: Defn.Def            => add(owner, isObj, d.name, Kind.Def, d.mods, span(d, d.body))
          case d: Ctor.Secondary      => add(owner, isObj, d.name, Kind.Constructor, d.mods)
          case d: Decl.Def            => add(owner, isObj, d.name, Kind.AbstractDef, d.mods)
          case d: Defn.Type           => add(owner, isObj, d.name, Kind.Type, d.mods)
          case d: Decl.Type           => add(owner, isObj, d.name, Kind.Type, d.mods)
          case d: Defn.Val            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Val, d.mods))
          case d: Decl.Val            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Val, d.mods))
          case d: Defn.Var            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Var, d.mods))
          case d: Decl.Var            => pats(d.pats).foreach(add(owner, isObj, _, Kind.Var, d.mods))
          case d: Defn.ExtensionGroup =>
            d.body match
              case Term.Block(stats) => stats.foreach(member(_, owner, false))
              case other             => member(other, owner, false)
          case _ => ()

        tree.stats.foreach(member(_, "", true))
        Right(out.result())
